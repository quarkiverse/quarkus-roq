package io.quarkus.cli.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.quarkus.cli.common.build.BuildSystemRunner;
import io.quarkus.cli.common.registry.ToggleRegistryClientMixin;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

/**
 * A cli command that delegates to the quarkus build system.
 */
public class BuildToolDelegatingCommand implements Callable<Integer> {

    private static final String GRADLE_NO_BUILD_CACHE = "--no-build-cache";
    private static final String GRADLE_NO_DAEMON = "--no-daemon";

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin(name = "output")
    protected OutputOptionMixin output;

    @CommandLine.Mixin
    protected ToggleRegistryClientMixin registryClient;

    @CommandLine.Mixin
    protected HelpOption helpOption;

    @CommandLine.ArgGroup(exclusive = false, validate = false)
    protected PropertiesOptions propertiesOptions = new PropertiesOptions();

    @CommandLine.Mixin
    private RunModeOption runMode;

    @CommandLine.ArgGroup(order = 1, exclusive = false, validate = false, heading = "%nBuild options:%n")
    private BuildOptions buildOptions = new BuildOptions();

    @Parameters(description = "Additional parameters passed to the build system")
    private List<String> params = new ArrayList<>();

    private Path projectRoot;

    private Path projectRoot() {
        if (projectRoot == null) {
            projectRoot = output.getTestDirectory();
            if (projectRoot == null) {
                projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            }
        }
        return projectRoot;
    }

    public BuildSystemRunner getRunner(BuildToolContext context) {
        return BuildSystemRunner.getRunner(output, context.getPropertiesOptions(), registryClient, context.getProjectRoot(),
                context.getBuildTool());
    }

    public void populateContext(BuildToolContext context) {
    }

    public Optional<BuildToolDelegatingCommand> getParentCommand() {
        return Optional.empty();
    }

    public final Integer call(BuildToolContext context) throws Exception {
        try {
            populateContext(context);
            prepare(context);
            BuildSystemRunner runner = getRunner(context);
            if (getParentCommand().isPresent()) {
                return getParentCommand().get().call(context);
            }

            if (context.getRunModeOption().isDryRun()) {
                output.info("Dry run option detected. Target command(s):");
            }
            String action = getAction(context)
                    .orElseThrow(() -> new IllegalStateException("Unknown action for " + runner.getBuildTool().name()));
            BuildSystemRunner.BuildCommandArgs commandArgs = runner.prepareAction(action, context.getBuildOptions(),
                    context.getRunModeOption(), context.getParams());
            if (context.getRunModeOption().isDryRun()) {
                output.info(" " + commandArgs.showCommand());
                return ExitCode.OK;
            }
            return runner.run(commandArgs);
        } catch (Exception e) {
            return output.handleCommandException(e, "Unable to build: " + e.getMessage());
        }
    }

    @Override
    public final Integer call() throws Exception {
        return call(new BuildToolContext(projectRoot(), runMode, buildOptions, propertiesOptions, new ArrayList<>(),
                new ArrayList<>(params)));
    }

    public void prepare(BuildToolContext context) {
        BuildSystemRunner runner = getRunner(context);
        if (runner.getBuildTool() == BuildTool.GRADLE) {
            prepareGradle(context);
        }

        if (runner.getBuildTool() == BuildTool.MAVEN) {
            prepareMaven(context);
        }
    }

    public void prepareMaven(BuildToolContext context) {
        // No pre-step by default
    }

    public void prepareGradle(BuildToolContext context) {
        // No pre-step by default
    }

    public Map<BuildTool, String> getActionMapping() {
        return Collections.emptyMap();
    }

    public Optional<String> getAction(BuildToolContext context) {
        return getParentCommand().map(cmd -> cmd.getAction(context))
                .orElse(Optional.ofNullable(getActionMapping().get(getRunner(context).getBuildTool())));
    }
}
