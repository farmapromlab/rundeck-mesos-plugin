package com.farmaprom.rundeck.plugin;

import com.dtolabs.rundeck.core.execution.ExecutionLogger;
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

import com.farmaprom.rundeck.plugin.constraint.ConstraintsChecker;
import com.farmaprom.rundeck.plugin.helpers.*;
import com.farmaprom.rundeck.plugin.logger.MessosHttpTail;
import com.farmaprom.rundeck.plugin.state.State;
import org.apache.mesos.v1.Protos;

import java.net.URI;

import org.apache.mesos.v1.Protos.*;

import java.net.UnknownHostException;
import java.util.*;

import static com.dtolabs.rundeck.core.Constants.ERR_LEVEL;
import static org.apache.mesos.v1.Protos.TaskState.TASK_FINISHED;
import static org.apache.mesos.v1.Protos.TaskState.TASK_KILLED;

@Plugin(name = MesosStepPlugin.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class MesosStepPlugin implements StepPlugin, Describable {
    static final String SERVICE_PROVIDER_NAME = "com.farmaprom.rundeck.plugin.MesosStepPlugin";

    public Description getDescription() {

        return DescriptionBuilder.builder()
                .name(SERVICE_PROVIDER_NAME)
                .title("Mesos run once")
                .description("Execute a Docker container on Mesos")
                .property(PropertyBuilder.builder()
                                .string("mesos_address")
                                .title("Mesos address")
                                .description("Mesos master address, http://master:5050/api/v1/scheduler")
                                .defaultValue("")
                                .required(true)
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
                        .string("mesos_role")
                        .title("Role")
                        .description("Role for for Mesos framework")
                        .defaultValue("*")
                        .required(false)
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
                        .defaultValue("0.1")
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .integer("docker_memory")
                        .title("Memory")
                        .description("How much memory your container needs.")
                        .required(true)
                        .defaultValue("128")
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

    private enum Reason implements FailureReason {
        ExampleReason
    }

    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws StepException {
        ExecutionLogger logger  = context.getExecutionContext().getExecutionLogger();

        String taskId = TaskIdGeneratorHelper.getTaskId(context);

        final Protos.FrameworkID frameworkID = Protos.FrameworkID.newBuilder().setValue(UUID.randomUUID().toString()).build();
        final State<FrameworkID> stateObject = new State<>(
                frameworkID,
                configuration.get("mesos_role").toString(),
                Double.parseDouble(configuration.get("docker_cpus").toString()),
                Double.parseDouble(configuration.get("docker_memory").toString()),
                "Rundeck Mesos Plugin: "
                        + context.getDataContextObject().get("job").get("name")
                        + " "
                        + context.getDataContextObject().get("job").get("execid"),
                getHostName()
        );

        final URI mesosUri = URI.create(configuration.get("mesos_address").toString());

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

        ContainerInfo.Builder containerInfo = createContainerInfo(
                configuration.get("docker_image").toString(),
                Boolean.parseBoolean(configuration.get("docker_force_pull").toString()),
                parameters,
                volumes
        );

        MessosHttpTail messosHttpTail = new MessosHttpTail(logger);

        MesosSchedulerClient scheduler = new MesosSchedulerClient(taskId, commandInfo, containerInfo, new ConstraintsChecker(""), stateObject, messosHttpTail, 1, logger);

        try {
            scheduler.init(mesosUri);

            closeStep(messosHttpTail, logger);

            scheduler.close();
        } catch(InterruptedException e) {
            scheduler.close();
            messosHttpTail.finishTail();

            scheduler = null;
            throw new StepException("Task status: " + TASK_KILLED, Reason.ExampleReason);
        }

        scheduler = null;
    }

    private static ContainerInfo.Builder createContainerInfo(String imageName, boolean forcePullImage, List<Protos.Parameter> parameters, List<Protos.Volume> volumes) {
        ContainerInfo.DockerInfo.Builder dockerInfoBuilder = ContainerInfo.DockerInfo.newBuilder();
        dockerInfoBuilder.setImage(imageName);
        dockerInfoBuilder.setNetwork(ContainerInfo.DockerInfo.Network.BRIDGE);
        dockerInfoBuilder.setForcePullImage(forcePullImage);
        if (!parameters.isEmpty()) {
            dockerInfoBuilder.addAllParameters(parameters);
        }

        ContainerInfo.Builder containerInfoBuilder = ContainerInfo.newBuilder();
        containerInfoBuilder.setType(ContainerInfo.Type.DOCKER);
        containerInfoBuilder.setDocker(dockerInfoBuilder.build());
        if (!volumes.isEmpty()) {
            containerInfoBuilder.addAllVolumes(volumes);
        }

        return containerInfoBuilder;
    }

    private static String getHostName()
    {
        String hostName = "";
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
        }

        return hostName;
    }

    private static void closeStep(MessosHttpTail messosHttpTail, ExecutionLogger logger) throws StepException {
        if (messosHttpTail.getTaskState() == null || messosHttpTail.getTaskState() != TASK_FINISHED) {
            if (messosHttpTail.getTaskStateMessage() != null && messosHttpTail.getTaskStateReason() != null) {
                logger.log(ERR_LEVEL, messosHttpTail.getTaskStateMessage());
                logger.log(ERR_LEVEL, messosHttpTail.getTaskStateReason().toString());
            }

            throw new StepException("Task status: " + messosHttpTail.getTaskState(), Reason.ExampleReason);
        }
    }
}