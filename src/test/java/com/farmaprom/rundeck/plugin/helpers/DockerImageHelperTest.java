package com.farmaprom.rundeck.plugin.helpers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class DockerImageHelperTest {

    private static final String DOCKER_IMAGE_NAME = "docker_image";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "v1.0.0", DOCKER_IMAGE_NAME + ":v1.0.0" },
                { "latest", DOCKER_IMAGE_NAME + ":latest" },
                { "master", DOCKER_IMAGE_NAME + ":master" },
        });
    }
    public DockerImageHelperTest(final String input, final String expected) {
        this.input = input;
        this.expected = expected;
    }

    private final String input;
    private final String expected;

    @Test
    public void testIsCorrectParseDockerImageTag() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("docker_image", DOCKER_IMAGE_NAME);
        configuration.put("docker_image_tag", input);

        assertEquals(DockerImageHelper.parseDockerImageTag(configuration), expected);
    }
}
