package io.quarkiverse.roq.cli;

import java.util.Map;

import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "start", mixinStandardHelpOptions = true, description = "Start the Roq site in dev mode")
public class StartCommand extends BuildToolDelegatingCommand {

    @Option(names = { "-p",
            "--port" }, defaultValue = "-1", fallbackValue = "0", arity = "0..1", description = "HTTP port (random if not specified)")
    private int port;

    @Override
    public void populateContext(BuildToolContext context) {
        RoqMain.requireRoqProject("start");
        if (port >= 0) {
            context.getPropertiesOptions().properties.put("quarkus.http.port", String.valueOf(port));
        }
    }

    @Override
    public void prepareMaven(BuildToolContext context) {
        // Skip resources:resources, dev mode handles resources itself
    }

    @Override
    public Map<BuildTool, String> getActionMapping() {
        return Map.of(
                BuildTool.MAVEN, "quarkus:dev",
                BuildTool.GRADLE, "quarkusDev",
                BuildTool.GRADLE_KOTLIN_DSL, "quarkusDev");
    }
}
