package com.farmaprom.helpers;

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import org.apache.mesos.MesosSchedulerDriver;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class MesosSchedulerDriverHelper {

    public static MesosSchedulerDriver createMesosSchedulerDriver(
            PluginStepContext context,
            Scheduler scheduler,
            Protos.FrameworkInfo.Builder frameworkBuilder,
            final Map<String, Object> configuration
    ) {
        String mesosMaster = configuration.get("mesos_address").toString();
        String mesosPrincipal = configuration.get("mesos_principal").toString();
        String mesosPassword = configuration.get("mesos_password").toString();

        MesosSchedulerDriver driver;
        if (!Objects.equals(mesosPrincipal, "") && !Objects.equals(mesosPassword, "")) {

            String password;
            try {
                password = getPrivateKeyStorageData(context.getExecutionContext(), mesosPassword);
            } catch (IOException e) {
                password = "";
            }

            Protos.Credential credential = Protos.Credential.newBuilder()
                    .setPrincipal(mesosPrincipal)
                    .setSecret(password)
                    .build();

            frameworkBuilder.setPrincipal(mesosPrincipal);
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster, credential);
        } else {
            frameworkBuilder.setPrincipal("rundeck-mesos-plugin-no-authenticate");
            driver = new MesosSchedulerDriver(scheduler, frameworkBuilder.build(), mesosMaster);
        }

        return driver;
    }

    private static String getPrivateKeyStorageData(ExecutionContext executionContext, String path) throws IOException {
        ResourceMeta contents = executionContext.getStorageTree().getResource(path).getContents();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);

        return new String(byteArrayOutputStream.toByteArray());
    }
}
