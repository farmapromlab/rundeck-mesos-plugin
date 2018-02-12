package com.farmaprom.rundeck.plugin.helpers;

import java.util.Map;

public class DockerImageHelper {

    public static String parseDockerImageTag(final Map<String, Object> configuration) {
        String dockerImageTag = configuration.get("docker_image_tag").toString().trim();

        if (!dockerImageTag.equals("")) {
            return configuration.get("docker_image").toString() + ":" + dockerImageTag;
        }

        return configuration.get("docker_image").toString();
    }
}
