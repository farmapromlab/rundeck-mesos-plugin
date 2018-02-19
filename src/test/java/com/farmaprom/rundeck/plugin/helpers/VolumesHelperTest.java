package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VolumesHelperTest {

    @Test
    public void testIsCorrectCreateVolumesBuilder() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("docker_volumes", "/tmp/docker:/var/docker\n/tmp/mesos:/var/mesos\n/tmp/rundeck:/tmp:tmp");

        List<Protos.Volume> volumes = new ArrayList<>();

        volumes.add(Protos.Volume.newBuilder().setHostPath("/tmp/docker").setContainerPath("/var/docker").setMode(Protos.Volume.Mode.RW).build());
        volumes.add(Protos.Volume.newBuilder().setHostPath("/tmp/mesos").setContainerPath("/var/mesos").setMode(Protos.Volume.Mode.RW).build());
        volumes.add(Protos.Volume.newBuilder().setHostPath("/tmp/rundeck").setContainerPath("/tmp:tmp").setMode(Protos.Volume.Mode.RW).build());

        assertEquals(VolumesHelper.createVolumesBuilder(configuration), volumes);
    }
}
