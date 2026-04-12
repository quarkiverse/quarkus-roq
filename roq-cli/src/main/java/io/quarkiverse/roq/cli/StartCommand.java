package io.quarkiverse.roq.cli;

import java.util.Map;

import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine.Command;

@Command(name = "start", mixinStandardHelpOptions = true, description = "Start the Roq site in dev mode")
public class StartCommand extends BuildToolDelegatingCommand {

    @Override
    public void populateContext(BuildToolContext context) {
        RoqMain.requireRoqProject("start");
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
