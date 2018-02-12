package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UrisHelper {

    public static List<Protos.CommandInfo.URI> crateUrisBuilder(final Map<String, Object> configuration)
    {
        List<Protos.CommandInfo.URI> uris = new ArrayList<>();

        String mesosFetcher = configuration.get("mesos_fetcher").toString().trim();
        if (!mesosFetcher.isEmpty()) {
            String[] split = mesosFetcher.split("\\r?\\n");
            for (String uri : split) {
                uris.add(
                    Protos.CommandInfo.URI.newBuilder().setValue(uri).setExtract(true).setExecutable(false).setCache(false).build()
                );
            }
        }

        return uris;
    }
}
