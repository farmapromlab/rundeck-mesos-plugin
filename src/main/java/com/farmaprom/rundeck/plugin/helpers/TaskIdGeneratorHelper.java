package com.farmaprom.rundeck.plugin.helpers;

import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import org.apache.commons.lang.StringUtils;
import java.util.UUID;

public class TaskIdGeneratorHelper {

    public static String getTaskId(PluginStepContext context) {

        String taskId = context.getDataContextObject().get("job").get("project")
                + "-" + context.getDataContextObject().get("job").get("name")
                + "-" + UUID.randomUUID().toString();

        return StringUtils.replace(taskId.toLowerCase(), " ", "-");
    }
}
