package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import io.quarkus.cli.common.OutputOptionMixin;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create a new Roq site")
public class CreateCommand implements Callable<Integer> {

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @Parameters(index = "0", description = "Project name (used as artifactId and directory name)")
    private String name;

    @Option(names = { "-g", "--group-id" }, defaultValue = "io.acme", description = "Project group ID")
    private String groupId;

    @Option(names = { "--version" }, defaultValue = "1.0.0-SNAPSHOT", description = "Project version")
    private String version;

    @Option(names = { "-x",
            "--extension" }, split = ",", description = "Extensions to add (e.g. theme-default, plugin-tagging, or full GAV)")
    private List<String> extra;

    @Option(names = { "--roq-version" }, description = "Roq version (default: latest)")
    private String roqVersion;

    @Option(names = { "--no-code" }, description = "Don't generate example code")
    private boolean noCode;

    @Option(names = { "--gradle" }, description = "Use Gradle instead of Maven")
    private boolean gradle;

    @Option(names = { "--gradle-kotlin-dsl" }, description = "Use Gradle with Kotlin DSL")
    private boolean gradleKotlinDsl;

    @Override
    public Integer call() {
        try {
            Path projectDir = Path.of(".").toAbsolutePath().normalize().resolve(name);

            if (Files.exists(projectDir)) {
                output.error("Directory already exists: " + projectDir);
                return CommandLine.ExitCode.USAGE;
            }

            BuildTool buildTool = BuildTool.MAVEN;
            if (gradleKotlinDsl) {
                buildTool = BuildTool.GRADLE_KOTLIN_DSL;
            } else if (gradle) {
                buildTool = BuildTool.GRADLE;
            }

            output.info("Creating Roq site: " + name);

            boolean success = new RoqProjectCreator(projectDir, name)
                    .groupId(groupId)
                    .version(version)
                    .roqVersion(roqVersion)
                    .buildTool(buildTool)
                    .noCode(noCode)
                    .extensions(extra)
                    .create();

            if (success) {
                output.info("\nRoq site created in ./" + name);
                output.info("Next steps:");
                output.info("  cd " + name);
                output.info("  roq start");
                return CommandLine.ExitCode.OK;
            } else {
                output.error("Failed to create project");
                return CommandLine.ExitCode.SOFTWARE;
            }
        } catch (Exception e) {
            return output.handleCommandException(e, "Unable to create project: " + e.getMessage());
        }
    }
}
