package io.quarkiverse.roq.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import io.quarkus.cli.common.BuildOptions;
import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.cli.common.RunModeOption;
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

    /**
     * Generates the site in a directory other than the current one, as {@code roq migrate} does with the site it
     * migrated.
     *
     * @return the exit code of the build
     */
    int generate(Path projectDir) throws Exception {
        return call(new BuildToolContext(projectDir, new RunModeOption(), new BuildOptions(), propertiesOptions,
                new ArrayList<>(), new ArrayList<>()));
    }

}
