package io.quarkiverse.roq.cli;

import org.eclipse.microprofile.config.ConfigProvider;

import picocli.CommandLine;

public class RoqVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class)
                .orElse("dev");
        return new String[] { "roq " + version };
    }
}
