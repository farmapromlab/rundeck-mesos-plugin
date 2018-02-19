package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ParametersHelperTest {

    @Test
    public void testIsCorrectCreateParametersBuilder() {

        Map<String, Object> configuration = new HashMap<>();
        configuration.put("docker_parameters", "param1=foo\nparam2=bar\nparam3=value=value");

        List<Protos.Parameter> parameters = new ArrayList<>();

        parameters.add(Protos.Parameter.newBuilder().setKey("param1").setValue("foo").build());
        parameters.add(Protos.Parameter.newBuilder().setKey("param2").setValue("bar").build());
        parameters.add(Protos.Parameter.newBuilder().setKey("param3").setValue("value=value").build());

        assertEquals(ParametersHelper.createParametersBuilder(configuration), parameters);
    }
}
