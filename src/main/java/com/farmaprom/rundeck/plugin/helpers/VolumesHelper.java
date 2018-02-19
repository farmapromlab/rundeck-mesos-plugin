package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolumesHelper {

    public static List<Protos.Volume> createVolumesBuilder(final Map<String, Object> configuration)
    {
        List<Protos.Volume> volumes = new ArrayList<>();

        String mesosFetcher = configuration.get("docker_volumes").toString().trim();
        if (!mesosFetcher.isEmpty()) {
            String[] split = mesosFetcher.split("\\r?\\n");
            for (String lineVolume : split) {
                String[] volumeArray = lineVolume.split(":", 2);
                if (volumeArray.length == 2) {
                    Protos.Volume volume = Protos.Volume.newBuilder()
                            .setHostPath(volumeArray[0])
                            .setContainerPath(volumeArray[1])
                            .setMode(Protos.Volume.Mode.RW)
                            .build();

                    volumes.add(volume);
                }
            }
        }

        return volumes;
    }
}
