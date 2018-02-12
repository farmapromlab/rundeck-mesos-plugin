package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class UrisHelperTest {

    private static final String URIS = "file:///etc/hub.tar.gz";

    @Test
    public void testIsCorrectCrateUrisBuilder() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("mesos_fetcher", URIS);

        List<Protos.CommandInfo.URI> uris = new ArrayList<>();

        uris.add(Protos.CommandInfo.URI.newBuilder().setValue(URIS).setExtract(true).setExecutable(false).setCache(false).build());

        assertEquals(UrisHelper.crateUrisBuilder(configuration), uris);
    }
}
