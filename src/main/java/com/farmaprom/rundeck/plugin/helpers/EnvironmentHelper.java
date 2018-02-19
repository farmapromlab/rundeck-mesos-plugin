package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;

import java.util.Map;

public class EnvironmentHelper {

    public static Protos.Environment.Builder createEnvironmentBuilder(final Map<String, Object> configuration) {
        Protos.Environment.Builder environment = Protos.Environment.newBuilder();

        String dockerEnvVars = configuration.get("docker_env_vars").toString().trim();

        if (!dockerEnvVars.isEmpty()) {
            String[] split = dockerEnvVars.split("\\r?\\n");

            for (String lineEnvironment : split) {
                String[] environmentArray = lineEnvironment.split("=", 2);

                if (environmentArray.length == 2) {
                    Protos.Environment.Variable variable = Protos.Environment.Variable.newBuilder()
                            .setName(environmentArray[0])
                            .setValue(environmentArray[1])
                            .build();
                    environment.addVariables(variable);
                }
            }
        }

        return environment;
    }

    public static  Protos.Environment createMesosTaskIdEnvironment(String taskId)
    {
        Protos.Environment.Builder environment = Protos.Environment.newBuilder();

        environment.addVariables(Protos.Environment.Variable.newBuilder()
                .setName("MESOS_TASK_ID")
                .setValue(taskId)
                .build());

        return environment.build();
    }
}
