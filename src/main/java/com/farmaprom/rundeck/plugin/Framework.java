package com.farmaprom.rundeck.plugin;

import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.farmaprom.rundeck.plugin.helpers.TaskIdGeneratorHelper;
import org.apache.mesos.v1.Protos;
import org.apache.mesos.v1.Protos.*;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos.Call;
import org.apache.mesos.v1.scheduler.Protos.Event;
import org.apache.mesos.v1.scheduler.Scheduler;

import com.farmaprom.rundeck.plugin.logger.LoggerWrapper;
import com.farmaprom.rundeck.plugin.constraint.ConstraintsChecker;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Framework {

    protected static boolean finished = false;

    static class DockerScheduler  implements Scheduler {

        private FrameworkInfo framework;
        private FrameworkID frameworkId;
        private State state;
        private Timer retryTimer = null;
        private Lock lock;
        private Condition finishedCondition;
        private boolean teardown;

        private final Protos.CommandInfo.Builder commandInfoBuilder;
        private final List<Protos.Volume> volumes;
        private final List<Protos.Parameter> parameters;
        private final PluginStepContext context;

        private final int totalTasks = 1;
        private final String imageName;
        private final boolean forcePullImage;
        private final ConstraintsChecker constraints;
        private final Double cpu;
        private final Double memory;

        private final LoggerWrapper loggerWrapper;

        private int launchedTasks = 0;
        private int finishedTasks = 0;

        DockerScheduler(
                FrameworkInfo framework,
                LoggerWrapper loggerWrapper,
                Lock lock,
                Condition finishedCondition,
                Protos.CommandInfo.Builder commandInfoBuilder,
                List<Protos.Volume> volumes,
                List<Protos.Parameter> parameters,
                final Map<String, Object> configuration,
                PluginStepContext context

        ) {
            this.framework = framework;
            this.loggerWrapper = loggerWrapper;
            this.lock = lock;
            this.finishedCondition = finishedCondition;

            this.commandInfoBuilder = commandInfoBuilder;
            this.volumes = volumes;
            this.parameters = parameters;
            this.context = context;

            this.state = State.DISCONNECTED;
            finished = false;

            this.imageName = configuration.get("docker_image").toString();
            this.cpu = Double.parseDouble(configuration.get("docker_cpus").toString());
            this.memory = Double.parseDouble(configuration.get("docker_memory").toString());
            this.forcePullImage = Boolean.parseBoolean(configuration.get("docker_force_pull").toString());
            this.constraints = new ConstraintsChecker(configuration.get("mesos_constraints").toString());
        }

        @Override
        public synchronized void connected(final Mesos mesos) {
            if (teardown) {
                return;
            }

            loggerWrapper.debug("Connected");

            state = State.CONNECTED;

            retryTimer = new Timer();
            retryTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    doReliableRegistration(mesos);
                }
            }, 0, 1000);
        }

        @Override
        public synchronized void disconnected(Mesos mesos) {
            loggerWrapper.debug("Disconnected");

            state = State.DISCONNECTED;
            cancelRetryTimer();
        }

        @Override
        public synchronized void received(Mesos mesos, Event event) {
            switch (event.getType()) {
                case SUBSCRIBED: {
                    frameworkId = event.getSubscribed().getFrameworkId();

                    loggerWrapper.frameworkID = frameworkId;
                    loggerWrapper.masterInfo = event.getSubscribed().getMasterInfo();

                    state = State.SUBSCRIBED;

                    loggerWrapper.debug(
                            "Subscribed with ID " + frameworkId + " to master " +
                                    event.getSubscribed().getMasterInfo().getId());
                    break;
                }

                case OFFERS: {
                    loggerWrapper.debug("Received an OFFERS event");

                    offers(mesos, event.getOffers().getOffersList(), loggerWrapper);
                    break;
                }

                case RESCIND: {
                    loggerWrapper.debug("Received an RESCIND event");
                    break;
                }

                case UPDATE: {
                    loggerWrapper.debug("Received an UPDATE event");

                    update(mesos, event.getUpdate().getStatus(), loggerWrapper);
                    break;
                }

                case MESSAGE: {
                    loggerWrapper.debug("Received a MESSAGE event");
                    break;
                }

                case FAILURE: {
                    loggerWrapper.debug("Received a FAILURE event");
                    break;
                }

                case ERROR: {
                    loggerWrapper.debug("Received an ERROR event");
                }

                case HEARTBEAT: {
                    loggerWrapper.debug("Received a HEARTBEAT event");
                    break;
                }

                case UNKNOWN: {
                    loggerWrapper.debug("Received an UNKNOWN event");
                    break;
                }
            }
        }

        private synchronized void doReliableRegistration(Mesos mesos) {
            if (state == State.SUBSCRIBED || state == State.DISCONNECTED) {
                cancelRetryTimer();
                return;
            }

            Call.Builder callBuilder = Call.newBuilder()
                    .setType(Call.Type.SUBSCRIBE)
                    .setSubscribe(Call.Subscribe.newBuilder()
                            .setFrameworkInfo(framework)
                            .build());

            mesos.send(callBuilder.build());
        }

        private void cancelRetryTimer() {
            if (retryTimer != null) {
                retryTimer.cancel();
                retryTimer.purge();
            }

            retryTimer = null;
        }

        private void offers(Mesos mesos, List<Offer> offers, LoggerWrapper loggerWrapper) {

            for (Offer offer : offers) {

                Double offerCpus = 0d;
                Double offerMem = 0d;
                for (Resource resource : offer.getResourcesList()) {
                    if (resource.getName().equals("cpus")) {
                        offerCpus += resource.getScalar().getValue();
                    } else if (resource.getName().equals("mem")) {
                        offerMem += resource.getScalar().getValue();
                    }
                }

                loggerWrapper.debug("offerCpus " + offerCpus);
                loggerWrapper.debug("cpu " + cpu);
                loggerWrapper.debug("cpu " + (offerCpus >= cpu));
                loggerWrapper.debug("offerMem " + offerMem);
                loggerWrapper.debug("memory " + memory);
                loggerWrapper.debug("memory " + (offerMem >= memory));
                loggerWrapper.debug("constraintsAllow " + constraints.constraintsAllow(offer));

                if (launchedTasks < totalTasks && offerCpus >= cpu && offerMem >= memory && constraints.constraintsAllow(offer)) {
                    Offer.Operation.Launch.Builder launch = Offer.Operation.Launch.newBuilder();

                    TaskID taskId = TaskID.newBuilder().setValue(TaskIdGeneratorHelper.getTaskId(context)).build();

                    loggerWrapper.debug("Launching task " + taskId.getValue() + " using offer " + offer.getId().getValue());

                    launchedTasks++;

                    // docker image info
                    ContainerInfo.DockerInfo.Builder dockerInfoBuilder = ContainerInfo.DockerInfo.newBuilder();
                    dockerInfoBuilder.setImage(imageName);
                    dockerInfoBuilder.setNetwork(ContainerInfo.DockerInfo.Network.BRIDGE);
                    dockerInfoBuilder.setForcePullImage(forcePullImage);
                    if (!parameters.isEmpty()) {
                        dockerInfoBuilder.addAllParameters(parameters);
                    }

                    // container info
                    ContainerInfo.Builder containerInfoBuilder = ContainerInfo.newBuilder();
                    containerInfoBuilder.setType(ContainerInfo.Type.DOCKER);
                    containerInfoBuilder.setDocker(dockerInfoBuilder.build());
                    if (!volumes.isEmpty()) {
                        containerInfoBuilder.addAllVolumes(volumes);
                    }

                    TaskInfo task = TaskInfo.newBuilder()
                            .setName("task " + taskId.getValue())
                            .setTaskId(taskId)
                            .setAgentId(offer.getAgentId())
                            .addResources(Resource.newBuilder()
                                    .setName("cpus")
                                    .setType(Value.Type.SCALAR)
                                    .setScalar(Value.Scalar.newBuilder()
                                            .setValue(cpu)
                                            .build()))
                            .addResources(Resource.newBuilder()
                                    .setName("mem")
                                    .setType(Value.Type.SCALAR)
                                    .setScalar(Value.Scalar.newBuilder()
                                            .setValue(memory)
                                            .build()))
                            .setContainer(containerInfoBuilder.build())
                            .setCommand(commandInfoBuilder)
                            .build();

                    launch.addTaskInfos(TaskInfo.newBuilder(task));

                    mesos.send(Call.newBuilder()
                            .setType(Call.Type.ACCEPT)
                            .setFrameworkId(frameworkId)
                            .setAccept(Call.Accept.newBuilder()
                                    .addOfferIds(offer.getId())
                                    .addOperations(Offer.Operation.newBuilder()
                                            .setType(Offer.Operation.Type.LAUNCH)
                                            .setLaunch(launch)
                                            .build())
                                    .setFilters(Filters.newBuilder()
                                            .setRefuseSeconds(1)
                                            .build()))
                            .build());

                    loggerWrapper.task = task;
                } else {
                    mesos.send(Call.newBuilder()
                            .setType(Call.Type.DECLINE)
                            .setFrameworkId(frameworkId)
                            .setDecline(Call.Decline.newBuilder().addOfferIds(offer.getId()))
                            .build());

                    mesos.send(Call.newBuilder()
                            .setType(Call.Type.SUPPRESS)
                            .setFrameworkId(frameworkId)
                            .build());

                }
            }
        }

        private void update(Mesos mesos, TaskStatus status, LoggerWrapper loggerWrapper) {

            loggerWrapper.debug(
                    "Status update: task " + status.getTaskId().getValue() +
                            " is in state " + status.getState().getValueDescriptor().getName());

            loggerWrapper.taskStatus = status;

            if (status.getState() == TaskState.TASK_FINISHED) {
                finishedTasks++;
                if (finishedTasks == totalTasks) {


                    lock.lock();
                    try {
                        finished = true;
                        finishedCondition.signal();
                    } finally {
                        lock.unlock();
                    }

                    teardown = true;
                }
            }

            if (status.getState() == TaskState.TASK_LOST ||
                    status.getState() == TaskState.TASK_KILLED ||
                    status.getState() == TaskState.TASK_FAILED) {
                loggerWrapper.error(
                        "Aborting because task " + status.getTaskId().getValue() +
                                " is in unexpected state " +
                                status.getState().getValueDescriptor().getName() +
                                " with reason '" +
                                status.getReason().getValueDescriptor().getName() + "'" +
                                " from source '" +
                                status.getSource().getValueDescriptor().getName() + "'" +
                                " with message '" + status.getMessage() + "'");

                teardown = true;
            }

            loggerWrapper.stoptMesosTailWait();

            mesos.send(Call.newBuilder()
                    .setType(Call.Type.ACKNOWLEDGE)
                    .setFrameworkId(frameworkId)
                    .setAcknowledge(Call.Acknowledge.newBuilder()
                            .setAgentId(status.getAgentId())
                            .setTaskId(status.getTaskId())
                            .setUuid(status.getUuid())
                            .build())
                    .build());
        }


        private enum State {
            DISCONNECTED,
            CONNECTED,
            SUBSCRIBED
        }

    }

    public static void teardownFramework(Mesos mesos, LoggerWrapper loggerWrapper)
    {
        if (loggerWrapper.frameworkID != null) {
            mesos.send(org.apache.mesos.v1.scheduler.Protos.Call.newBuilder()
                    .setType(org.apache.mesos.v1.scheduler.Protos.Call.Type.TEARDOWN)
                    .setFrameworkId(loggerWrapper.frameworkID).build());
        }
    }
}
