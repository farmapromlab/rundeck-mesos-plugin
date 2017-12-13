package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParametersHelper {

    public static List<Protos.Parameter> createParametersBuilder(final Map<String, Object> configuration)
    {
        List<Protos.Parameter> parameters = new ArrayList<>();

        String dockerParameters = configuration.get("docker_parameters").toString();
        if (!dockerParameters.isEmpty()) {
            String[] splits = dockerParameters.split("\\r?\\n");
            for (String split : splits) {
                String[] pairs = split.split("=");

                Protos.Parameter uri = Protos.Parameter.newBuilder()
                        .setKey(pairs[0])
                        .setValue(pairs[1])
                        .build();

                parameters.add(uri);
            }
        }

        return parameters;
    }
}
