package com.farmaprom.rundeck.plugin;

import com.dtolabs.rundeck.core.execution.ExecutionLogger;
import com.farmaprom.rundeck.plugin.logger.MessosHttpTail;
import com.farmaprom.rundeck.plugin.state.State;
import com.farmaprom.rundeck.plugin.state.Tuple;
import com.mesosphere.mesos.rx.java.AwaitableSubscription;
import com.mesosphere.mesos.rx.java.MesosClientBuilder;
import com.mesosphere.mesos.rx.java.SinkOperation;
import com.mesosphere.mesos.rx.java.SinkOperations;
import com.mesosphere.mesos.rx.java.protobuf.ProtoUtils;
import com.mesosphere.mesos.rx.java.protobuf.ProtobufMesosClientBuilder;
import com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls;
import org.apache.mesos.v1.Protos;
import org.apache.mesos.v1.Protos.*;
import org.apache.mesos.v1.scheduler.Protos.Call;
import org.apache.mesos.v1.scheduler.Protos.Event;
import com.farmaprom.rundeck.plugin.constraint.ConstraintsChecker;
import rx.Observable;

import java.net.URI;
import java.util.*;

import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;
import static com.dtolabs.rundeck.core.Constants.ERR_LEVEL;
import static com.google.common.collect.Lists.newArrayList;
import static com.mesosphere.mesos.rx.java.SinkOperations.sink;
import static com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls.decline;
import static com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls.subscribe;
import static com.mesosphere.mesos.rx.java.util.UserAgentEntries.userAgentEntryForMavenArtifact;
import static java.util.stream.Collectors.groupingBy;
import static rx.Observable.from;
import static rx.Observable.just;

public class MesosSchedulerClient {

    private final String taskId;
    private final CommandInfo.Builder commandInfo;
    private final ContainerInfo.Builder containerInfo;
    private final ConstraintsChecker constraints;
    private final State<FrameworkID> state;
    private final ExecutionLogger logger;

    private int desiredInstances;
    private int launchedTasks;

    private AwaitableSubscription openStream;
    private Thread subscriberThread;
    private MessosHttpTail messosHttpTail;

    MesosSchedulerClient(
            String taskId,
            CommandInfo.Builder commandInfo,
            ContainerInfo.Builder containerInfo,
            ConstraintsChecker constraints,
            final State<FrameworkID> state,
            MessosHttpTail messosHttpTail,
            int desiredInstances,
            ExecutionLogger logger
    ) {
        this.taskId = taskId;
        this.commandInfo = commandInfo;
        this.containerInfo = containerInfo;
        this.constraints = constraints;
        this.state = state;
        this.messosHttpTail = messosHttpTail;
        this.desiredInstances = desiredInstances;
        this.logger = logger;

        this.launchedTasks = 0;

    }

    public void init(
            final URI mesosUri
    ) throws InterruptedException {

        if (openStream == null || openStream.isUnsubscribed()) {

            if (subscriberThread != null) {
                subscriberThread.interrupt();
            }

            subscriberThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        connect(mesosUri);
                    }
                }
            });

            Thread messosHttpTailThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    {
                        synchronized (this) {
                            try {
                                messosHttpTail.initTailStdOut();
                            } catch (Exception e) {
                                e.printStackTrace();
                                messosHttpTail.finishTail();
                            }
                        }
                    }
                }
            });

            subscriberThread.start();
            messosHttpTailThread.start();

            subscriberThread.join();
            messosHttpTailThread.join();

            messosHttpTail.initTailStdErr();
        }
    }

    public void close() {
        if (openStream != null) {
            if (!openStream.isUnsubscribed()) {
                openStream.unsubscribe();
            }
        }
    }

    private void connect(
            final URI mesosUri
    ) {
        final MesosClientBuilder<Call, Event> clientBuilder = ProtobufMesosClientBuilder.schedulerUsingProtos()
                .mesosUri(mesosUri)
                .applicationUserAgentEntry(userAgentEntryForMavenArtifact("com.farmaprom.rundeck", "rundeck-mesos-farmaprom-framework"));

        final Call subscribeCall = subscribe(
                state.getFwId(),
                FrameworkInfo.newBuilder()
                        .setId(state.getFwId())
                        .setUser(System.getProperty("user.name", "default-rundeck-user"))
                        .setName(state.getName())
                        .setFailoverTimeout(0)
                        .setHostname(state.getHostName())
                        .setRole(state.getResourceRole())
                        .build()
        );

        final Observable<State<FrameworkID>> stateObservable = just(state).repeat();

        clientBuilder
                .subscribe(subscribeCall)
                .processStream(unicastEvents -> {
                    final Observable<Event> events = unicastEvents.share();

                    final Observable<Optional<SinkOperation<Call>>> offerEvaluations = events
                            .filter(event -> event.getType() == Event.Type.OFFERS)
                            .flatMap(event -> from(event.getOffers().getOffersList()))
                            .zipWith(stateObservable, Tuple::create)
                            .map(this::handleOffer)
                            .map(Optional::of);

                    final Observable<Optional<SinkOperation<Call>>> offerSuppress = events
                            .filter(event -> event.getType() == Event.Type.OFFERS)
                            .flatMap(event -> from(event.getOffers().getOffersList()))
                            .zipWith(stateObservable, Tuple::create)
                            .map(this::suppressOffer)
                            .map(Optional::ofNullable);

                    final Observable<Optional<SinkOperation<Call>>> updateStatusAck = events
                            .filter(event -> event.getType() == Event.Type.UPDATE && event.getUpdate().getStatus().hasUuid())
                            .zipWith(stateObservable, Tuple::create)
                            .map((Tuple<Event, State<FrameworkID>> t) -> {
                                final TaskStatus status = t._1.getUpdate().getStatus();

                                messosHttpTail.setFrameworkId(state.getFwId());
                                messosHttpTail.setExecutorId(status.getExecutorId());
                                messosHttpTail.setAgentId(status.getAgentId());
                                messosHttpTail.setContainerId(status.getContainerStatus().getContainerId());
                                messosHttpTail.setTaskState(status.getState());
                                messosHttpTail.setTaskStateMessage(status.getMessage());
                                messosHttpTail.setTaskStateReason(status.getReason());

                                messosHttpTail.startTail();

                                logger.log(DEBUG_LEVEL, "Status update task " + status.getTaskId() + " is in state " + status.getState());

                                return SchedulerCalls.ackUpdate(state.getFwId(), status.getUuid(), status.getAgentId(), status.getTaskId());
                            })
                            .map(SinkOperations::create)
                            .map(Optional::of);

                    final Observable<Optional<SinkOperation<Call>>> errorLogger = events
                            .filter(event -> event.getType() == Event.Type.ERROR || (event.getType() == Event.Type.UPDATE && event.getUpdate().getStatus().getState() == TaskState.TASK_ERROR))
                            .doOnNext(event -> logger.log(ERR_LEVEL, "Task Error: " + ProtoUtils.protoToString(event)))
                            .map(event -> Optional.empty());

                    final Observable<Optional<SinkOperation<Call>>> terminate = events
                            .filter(event -> event.getType() == Event.Type.UPDATE && Arrays.asList(State.finalTaskStatuses).contains(event.getUpdate().getStatus().getState()))
                            .zipWith(stateObservable, Tuple::create)
                            .map((Tuple<Event, State<FrameworkID>> t) -> {
                                logger.log(DEBUG_LEVEL, "Framework teardown " + state.getFwId());
                                launchedTasks--;
                                return teardown(state.getFwId());
                            })
                            .map(SinkOperations::sink)
                            .map(Optional::of);

                    return offerEvaluations
                            .mergeWith(updateStatusAck)
                            .mergeWith(offerSuppress)
                            .mergeWith(terminate)
                            .mergeWith(errorLogger);
                });


        com.mesosphere.mesos.rx.java.MesosClient<Call, Event> client = clientBuilder.build();

        openStream = client.openStream();

        try {
            openStream.await();
        } catch (Throwable e) {
            e.printStackTrace();
            close();
        }
    }

    private SinkOperation<Call> handleOffer(
            final Tuple<Offer, State<FrameworkID>> t
    ) {
        final Offer offer = t._1;
        final State<FrameworkID> state = t._2;

        final FrameworkID frameworkId = state.getFwId();
        final AgentID agentId = offer.getAgentId();
        final List<OfferID> ids = newArrayList(offer.getId());

        final Map<String, List<Resource>> resources = offer.getResourcesList()
                .stream()
                .collect(groupingBy(Resource::getName));
        final List<Resource> cpuList = resources.get("cpus");
        final List<Resource> memList = resources.get("mem");

        if (this.launchedTasks < this.desiredInstances && this.constraints.constraintsAllow(offer)) {

            messosHttpTail.setUrl(offer.getUrl());

            if (
                    cpuList != null && !cpuList.isEmpty()
                            && memList != null && !memList.isEmpty()
                            && cpuList.size() == memList.size()
                    ) {
                final List<TaskInfo> tasks = newArrayList();

                Double availableCpu = 0d;
                Double availableMem = 0d;
                for (int i = 0; i < cpuList.size(); i++) {
                    final Resource cpus = cpuList.get(i);
                    final Resource mem = memList.get(i);

                    availableCpu += cpus.getScalar().getValue();
                    availableMem += mem.getScalar().getValue();
                }

                final double cpusPerTask = state.getCpusPerTask();
                final double memMbPerTask = state.getMemMbPerTask();

                if (cpusPerTask <= availableCpu && memMbPerTask <= availableMem) {
                    this.launchedTasks++;

                    tasks.add(createTask(agentId, this.taskId, cpusPerTask, memMbPerTask));
                }

                if (!tasks.isEmpty()) {
                    logger.log(DEBUG_LEVEL, "Launching tasks " + tasks.size());

                    return sink(
                            taskAccept(frameworkId, ids, tasks),
                            (e) -> logger.log(ERR_LEVEL, e.toString())
                    );
                } else {
                    return sink(decline(frameworkId, ids));
                }
            } else {
                return sink(decline(frameworkId, ids));
            }
        } else {
            return sink(decline(frameworkId, ids));
        }
    }

    private SinkOperation<Call> suppressOffer(
            final Tuple<Offer, State<FrameworkID>> t
    ) {
        final Offer offer = t._1;
        final State<FrameworkID> state = t._2;
        final FrameworkID frameworkId = state.getFwId();
        final List<OfferID> ids = newArrayList(offer.getId());

        if (this.launchedTasks >= this.desiredInstances || !this.constraints.constraintsAllow(offer)) {
            return sink(suppress(frameworkId, ids));
        }

        return null;
    }

    private Call taskAccept(
            final FrameworkID frameworkId,
            final List<OfferID> offerIds,
            final List<TaskInfo> tasks
    ) {
        return Call.newBuilder()
                .setFrameworkId(frameworkId)
                .setType(Call.Type.ACCEPT)
                .setAccept(
                        Call.Accept.newBuilder()
                                .addAllOfferIds(offerIds)
                                .addOperations(
                                        Offer.Operation.newBuilder()
                                                .setType(Offer.Operation.Type.LAUNCH)
                                                .setLaunch(Offer.Operation.Launch.newBuilder().addAllTaskInfos(tasks))
                                )
                )
                .build();
    }

    private TaskInfo createTask(
            final AgentID agentId,
            final String taskId,
            final double cpus,
            final double mem
    ) {
        return TaskInfo.newBuilder()
                .setName(taskId)
                .setTaskId(TaskID.newBuilder().setValue(taskId))
                .setAgentId(agentId)
                .setContainer(this.containerInfo.build())
                .setCommand(this.commandInfo.build())
                .addResources(Resource.newBuilder()
                        .setName("cpus")
                        .setRole(this.state.getResourceRole())
                        .setType(Value.Type.SCALAR)
                        .setScalar(Value.Scalar.newBuilder().setValue(cpus)))
                .addResources(Resource.newBuilder()
                        .setName("mem")
                        .setRole(this.state.getResourceRole())
                        .setType(Value.Type.SCALAR)
                        .setScalar(Value.Scalar.newBuilder().setValue(mem)))
                .build();
    }

    private Call teardown(
            final FrameworkID frameworkId
    ) {
        return Call.newBuilder().setFrameworkId(frameworkId).setType(Call.Type.TEARDOWN).build();
    }

    private Call suppress(final Protos.FrameworkID frameworkId, final List<Protos.OfferID> offerIds)
    {
        return Call.newBuilder()
                .setFrameworkId(frameworkId)
                .setType(Call.Type.SUPPRESS)
                .setDecline(
                        Call.Decline.newBuilder()
                                .addAllOfferIds(offerIds)
                )
                .build();
    }
}
