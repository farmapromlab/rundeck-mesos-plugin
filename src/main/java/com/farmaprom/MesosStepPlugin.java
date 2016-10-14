package com.farmaprom;

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

import com.farmaprom.helpers.EnvironmentHelper;
import com.farmaprom.helpers.MesosSchedulerDriverHelper;
import com.farmaprom.helpers.MesosTaskHelper;
import com.farmaprom.helpers.UrisHelper;

import com.farmaprom.logger.LoggerWrapper;

import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;

import java.util.*;

import static org.apache.mesos.Protos.TaskState.TASK_FINISHED;

@Plugin(name = MesosStepPlugin.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class MesosStepPlugin implements StepPlugin, Describable {

    static final String SERVICE_PROVIDER_NAME = "com.dtolabs.rundeck.plugin.example.MesosStepPlugin";

    public Description getDescription() {

        return DescriptionBuilder.builder()
                .name(SERVICE_PROVIDER_NAME)
                .title("Mesos run once")
                .description("Execute a docker container on mesos")
                .property(PropertyBuilder.builder()
                        .string("mesos_address")
                        .title("Mesos address")
                        .description("Address to ZK mesos")
                        .required(true)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_principal")
                        .title("Mesos principal")
                        .description("Principal to mesos")
                        .defaultValue("")
                        .required(false)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_password")
                        .title("Mesos password")
                        .description("Password to mesos")
                        .renderingOption("selectionAccessor", StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
                        .renderingOption("valueConversion", StringRenderingConstants.ValueConversion.STORAGE_PATH_AUTOMATIC_READ)
                        .defaultValue("")
                        .required(false)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("mesos_fetcher")
                        .title("URIs")
                        .description("String of newline separated list of valid URIs. E.g. file:///etc/hub.tar.gz")
                        .required(false)
                        .defaultValue("")
                        .renderingOption("displayType", StringRenderingConstants.DisplayType.MULTI_LINE)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_image")
                        .title("Docker image")
                        .description("The docker Image to run")
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
                        .required(true)
                        .build()
                )
                .property(PropertyBuilder.builder()
                        .string("docker_cpus")
                        .title("Cpus")
                        .description("How many cpus your container needs.")
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
                        .description("String of newline separated bash env variables. E.g. FOO=foo\\nBAR=bar")
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

        FrameworkInfo.Builder frameworkBuilder = FrameworkInfo.newBuilder()
                .setName("Rundex Mesos PLugin")
                .setUser("") // Have Mesos fill in the current user.
                .setFailoverTimeout(0);

        Protos.CommandInfo.Builder commandInfo = Protos.CommandInfo.newBuilder()
                .setValue(configuration.get("docker_command").toString())
                .setShell(true);

        commandInfo.setEnvironment(EnvironmentHelper.crateEnvironmentBuilder(configuration).build());

        commandInfo.addAllUris(UrisHelper.crateUrisBuilder(configuration));

        Scheduler scheduler = new DockerScheduler(
                loggerWrapper,
                1,
                commandInfo,
                configuration
        );


        MesosSchedulerDriver driver = MesosSchedulerDriverHelper.createMesosSchedulerDriver(
                context,
                scheduler,
                frameworkBuilder,
                configuration
        );

        int status = driver.run() == Protos.Status.DRIVER_STOPPED ? 0 : 1;

        for (Map.Entry<Integer, String> message : loggerWrapper.getMessages().entrySet()) {
            System.out.println(message.getValue());
        }

        MesosTaskHelper.getMesosTaskOutput(loggerWrapper);

        if (TASK_FINISHED != loggerWrapper.taskStatus.getState()) {
            throw new StepException("Task status: " + loggerWrapper.taskStatus.getState(), Reason.ExampleReason);
        }
    }
}
