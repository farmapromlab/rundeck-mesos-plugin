package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EnvironmentHelperTest {

    private static final String EXEC_ID = "execid";

    @Test
    public void testIsCorrectCreateEnvironmentBuilder() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("docker_env_vars", "FOO=foo\nBAR=bar\nBAZ=baz=baz");

        Protos.Environment.Builder environment = Protos.Environment.newBuilder();
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("FOO").setValue("foo").build());
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("BAR").setValue("bar").build());
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("BAZ").setValue("baz=baz").build());

        assertEquals(EnvironmentHelper.createEnvironmentBuilder(configuration).toString(), environment.toString());
    }

    @Test
    public void testIsCorrectCreateMesosTaskIdEnvironment() {
        Protos.Environment.Builder environment = Protos.Environment.newBuilder();
        environment.addVariables(Protos.Environment.Variable.newBuilder().setName("MESOS_TASK_ID").setValue(EXEC_ID).build());

        assertEquals(EnvironmentHelper.createMesosTaskIdEnvironment(EXEC_ID), environment.build());
    }
}
