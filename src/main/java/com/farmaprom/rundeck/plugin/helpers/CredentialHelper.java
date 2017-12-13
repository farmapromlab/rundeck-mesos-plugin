package com.farmaprom.rundeck.plugin.helpers;

import org.apache.mesos.v1.Protos;

import java.util.Map;
import java.util.Objects;

public class CredentialHelper {

    public static Protos.Credential.Builder crateCredentialBuilder(Protos.FrameworkInfo.Builder frameworkBuilder, Map<String, Object> configuration)
    {
        Protos.Credential.Builder credentialBuilder = null;

        String mesosPrincipal = configuration.get("mesos_principal").toString();
        String mesosPassword = configuration.get("mesos_password").toString();

        if (!Objects.equals(mesosPrincipal, "")) {
            frameworkBuilder.setPrincipal(mesosPrincipal);

            if (!Objects.equals(mesosPassword, "")) {
                credentialBuilder = Protos.Credential.newBuilder()
                        .setPrincipal(mesosPrincipal)
                        .setSecret(mesosPassword);
            }
        }

        return credentialBuilder;
    }
}
