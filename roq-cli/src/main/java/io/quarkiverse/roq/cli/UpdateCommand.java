package io.quarkiverse.roq.cli;

import java.util.Map;

import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine.Command;

@Command(name = "update", mixinStandardHelpOptions = true, description = "Update the Roq/Quarkus project")
public class UpdateCommand extends BuildToolDelegatingCommand {

    @Override
    public void populateContext(BuildToolContext context) {
        RoqMain.requireRoqProject("update");
    }

    @Override
    public Map<BuildTool, String> getActionMapping() {
        return Map.of(
                BuildTool.MAVEN, "quarkus:update",
                BuildTool.GRADLE, "quarkusUpdate",
                BuildTool.GRADLE_KOTLIN_DSL, "quarkusUpdate");
    }
}
