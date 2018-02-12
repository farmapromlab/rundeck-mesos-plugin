package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EnvironmentHelperTest {

    @Test
    public void testIsCorrectCreateEnvironmentBuilder() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("docker_env_vars", "FOO=foo\nBAR=bar");

        Protos.Environment.Builder environment = Protos.Environment.newBuilder();
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("FOO").setValue("foo").build());
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("BAR").setValue("bar").build());

        assertEquals(EnvironmentHelper.createEnvironmentBuilder(configuration).toString(), environment.toString());
    }
}
