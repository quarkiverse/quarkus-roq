package io.quarkiverse.roq.cli;

import java.util.Map;

import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine.Command;

@Command(name = "generate", mixinStandardHelpOptions = true, description = "Generate the static site")
public class GenerateCommand extends BuildToolDelegatingCommand {

    private static final Map<BuildTool, String> ACTION_MAPPING = Map.of(
            BuildTool.MAVEN, "package",
            BuildTool.GRADLE, "build",
            BuildTool.GRADLE_KOTLIN_DSL, "build");

    @Override
    public void prepareMaven(BuildToolContext context) {
        // Skip resources:resources (already part of the package lifecycle)
        // and add the quarkus:run goal with -DskipTests
        context.getParams().add("quarkus:run");
        context.getParams().add("-DskipTests");
    }

    @Override
    public void prepareGradle(BuildToolContext context) {
        super.prepareGradle(context);
        context.getParams().add("quarkusRun");
        context.getParams().add("-x");
        context.getParams().add("test");
    }

    @Override
    public Map<BuildTool, String> getActionMapping() {
        return ACTION_MAPPING;
    }

    @Override
    public void populateContext(BuildToolContext context) {
        super.populateContext(context);
        context.getPropertiesOptions().properties.put("quarkus.roq.generator.batch", "true");
    }

}
