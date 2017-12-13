package com.farmaprom.rundeck.plugin;

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.dtolabs.rundeck.plugins.util.PropertyBuilder;

import com.farmaprom.rundeck.plugin.helpers.*;
import com.farmaprom.rundeck.plugin.logger.LoggerWrapper;
import com.farmaprom.rundeck.plugin.logger.Log;

import org.apache.mesos.v1.Protos;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Scheduler;
import org.apache.mesos.v1.scheduler.V1Mesos;

import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.mesos.v1.Protos.TaskState.TASK_FINISHED;
import static org.apache.mesos.v1.Protos.TaskState.TASK_KILLED;

@Plugin(name = MesosStepPlugin.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class MesosStepPlugin implements StepPlugin, Describable {

    static final String SERVICE_PROVIDER_NAME = "com.farmaprom.rundeck.plugin.MesosStepPlugin";

    private Mesos mesos;

    public Description getDescription() {

        return DescriptionBuilder.builder()
                .name(SERVICE_PROVIDER_NAME)
                .title("Mesos run once")
                .description("Execute a Docker container on Mesos")
                .property(PropertyBuilder.builder()
                        .string("mesos_address")
                        .title("Mesos address")
                        .description("Mesos master address, zk://localhost:2181/mesos")
                        .defaultValue("")
                        .required(true)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_principal")
                        .title("Mesos principal")
                        .description("Principal for Mesos framework authentication")
                        .defaultValue("")
                        .required(false)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_password")
                        .title("Mesos password")
                        .description("Password for Mesos framework authentication")
                        .renderingOption("selectionAccessor", StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
                        .renderingOption("valueConversion", StringRenderingConstants.ValueConversion.STORAGE_PATH_AUTOMATIC_READ)
                        .defaultValue("")
                        .required(false)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_fetcher")
                        .title("URIs")
                        .description("List of newline separated valid URIs. E.g. file:///etc/hub.tar.gz")
                        .required(false)
                        .defaultValue("")
                        .renderingOption("displayType", StringRenderingConstants.DisplayType.MULTI_LINE)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_constraints")
                        .title("Constraints")
                        .description("Comma-separated list of valid constraints. Valid constraint format is \"field:operator[:value]\".")
                        .defaultValue("")
                        .required(false)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_image")
                        .title("Docker image")
                        .description("The Docker image to run")
                        .defaultValue("")
                        .required(true)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .booleanType("docker_force_pull")
                        .title("Docker pull")
                        .description("Force pull image on every launch")
                        .defaultValue("false")
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_command")
                        .title("Docker command")
                        .description("What command should the container run.")
                        .defaultValue("")
                        .required(true)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .booleanType("docker_shell")
                        .title("Docker shell")
                        .description("Use custom docker command shell")
                        .defaultValue("false")
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_cpus")
                        .title("CPUs")
                        .description("How many CPUs your container needs.")
                        .required(true)
                        .defaultValue("")
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .integer("docker_memory")
                        .title("Memory")
                        .description("How much memory your container needs.")
                        .required(true)
                        .defaultValue("")
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_env_vars")
                        .title("Environment variables")
                        .description("List of newline separated bash environment variables. E.g. FOO=foo\\nBAR=bar")
                        .required(false)
                        .defaultValue("")
                        .renderingOption("displayType", StringRenderingConstants.DisplayType.MULTI_LINE)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_volumes")
                        .title("Volumes")
                        .description("List of newline separated valid volumes. E.g. /tmp/docker:/tmp/docker")
                        .required(false)
                        .defaultValue("")
                        .renderingOption("displayType", StringRenderingConstants.DisplayType.MULTI_LINE)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_parameters")
                        .title("Parameters")
                        .description("List of newline separated parameters. E.g. FOO=foo\\nBAR=bar")
                        .required(false)
                        .defaultValue("")
                        .renderingOption("displayType", StringRenderingConstants.DisplayType.MULTI_LINE)
                        .build()
                )
                .build();
    }

    private enum Reason implements FailureReason{
        ExampleReason
    }

    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws
            StepException {

        LoggerWrapper loggerWrapper = new LoggerWrapper();

        Lock lock = new ReentrantLock();

        Condition finishedCondition = lock.newCondition();

        String hostName = "";
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
        }

        Protos.FrameworkInfo.Builder frameworkBuilder = Protos.FrameworkInfo.newBuilder()
                .setUser(System.getProperty("user.name", "default-rundeck-user"))
                .setFailoverTimeout(0)
                .setHostname(hostName)
                .setName("Rundeck Mesos Plugin")
                .addCapabilities(Protos.FrameworkInfo.Capability.newBuilder().setType(Protos.FrameworkInfo.Capability.Type.TASK_KILLING_STATE));

        Boolean dockerShell = !Boolean.parseBoolean(configuration.get("docker_shell").toString());

        Protos.CommandInfo.Builder commandInfo = Protos.CommandInfo.newBuilder().setShell(dockerShell);

        if (dockerShell) {
            commandInfo.setValue(configuration.get("docker_command").toString());
        } else {
            List<String> arg = new ArrayList<>(Arrays.asList(configuration.get("docker_command").toString().split("\\s+")));
            commandInfo.addAllArguments(arg);
        }

        commandInfo.setEnvironment(EnvironmentHelper.createEnvironmentBuilder(configuration).build());

        commandInfo.addAllUris(UrisHelper.crateUrisBuilder(configuration));

        List<Protos.Volume> volumes = VolumesHelper.createVolumesBuilder(configuration);

        List<Protos.Parameter> parameters = ParametersHelper.createParametersBuilder(configuration);

        Protos.Credential.Builder credentialBuilder = CredentialHelper.crateCredentialBuilder(frameworkBuilder, configuration);

        Scheduler scheduler = new Framework.DockerScheduler(
                frameworkBuilder.build(),
                loggerWrapper,
                lock,
                finishedCondition,
                commandInfo,
                volumes,
                parameters,
                configuration,
                context
        );



        Thread mesosDriverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                {
                    synchronized (this) {
                        try {
                            String mesosAddress = configuration.get("mesos_address").toString();
                            if (credentialBuilder != null) {
                                mesos = new V1Mesos(scheduler, mesosAddress, credentialBuilder.build());
                            } else {
                                mesos = new V1Mesos(scheduler, mesosAddress);
                            }

                            lock.lock();
                            try {
                                while (!Framework.finished) {
                                    finishedCondition.await();
                                }
                            } finally {
                                lock.unlock();
                            }
                        } catch (Exception e) {
                            teardownFramework(loggerWrapper);
                        }
                    }
                }
            }
        });

        MesosTaskHelper mesosTaskHelper = new MesosTaskHelper();

        Thread mesosTaskTailThread = new Thread(new Runnable() {
            @Override
            public void run() {
                {
                    synchronized (this) {
                        try {
                            mesosTaskHelper.mesosTailStdOut(loggerWrapper);
                        } catch (Exception e) {
                            loggerWrapper.stoptMesosTailWait();
                            mesosTaskHelper.stopTail();
                        }
                    }
                }
            }
        });

        try {
            mesosDriverThread.start();
            mesosTaskTailThread.start();

            mesosDriverThread.join();
            mesosTaskTailThread.join();

            mesosTaskHelper.getMesosTaskOutput(loggerWrapper);

            teardownFramework(loggerWrapper);

            for (Map.Entry<Integer, Log> log : loggerWrapper.getMessages().entrySet()) {
                context.getLogger().log(log.getValue().getLevel(), log.getValue().getMessage());
            }

            if (loggerWrapper.taskStatus != null && TASK_FINISHED != loggerWrapper.taskStatus.getState()) {

                this.addTaskErrorLog(context, loggerWrapper);

                throw new StepException("Task status: " + loggerWrapper.taskStatus.getState(), Reason.ExampleReason);
            }

        } catch (CancellationException | InterruptedException e) {
            throw new StepException("Task status: " + TASK_KILLED, Reason.ExampleReason);
        } finally {
            teardownFramework(loggerWrapper);
            loggerWrapper.stoptMesosTailWait();
            mesosTaskHelper.stopTail();

            this.addTaskErrorLog(context, loggerWrapper);
        }
    }

    private void addTaskErrorLog(PluginStepContext context, LoggerWrapper loggerWrapper)
    {
        if (loggerWrapper.taskStatus != null) {
            context.getLogger().log(5, loggerWrapper.taskStatus.getMessage());
            context.getLogger().log(5, loggerWrapper.taskStatus.getReason().toString());
        }
    }

    private void teardownFramework(LoggerWrapper loggerWrapper)
    {
        if (mesos != null) {
            Framework.teardownFramework(mesos, loggerWrapper);
        }
    }
}
