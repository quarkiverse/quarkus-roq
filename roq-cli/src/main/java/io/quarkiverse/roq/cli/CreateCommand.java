package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import io.quarkus.cli.common.OutputOptionMixin;
import io.quarkus.devtools.commands.CreateProject;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create a new Roq site")
public class CreateCommand implements Callable<Integer> {

    private static final String ROQ_GROUP_ID = "io.quarkiverse.roq";
    private static final String ROQ_EXTENSION = ROQ_GROUP_ID + ":quarkus-roq";
    private static final String ROQ_PREFIX = ROQ_GROUP_ID + ":quarkus-roq-";

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

            Set<String> extensions = new HashSet<>();
            extensions.add(withVersion(ROQ_EXTENSION));

            if (extra != null) {
                for (String ext : extra) {
                    extensions.add(withVersion(resolveExtension(ext.trim())));
                }
            }

            BuildTool buildTool = BuildTool.MAVEN;
            if (gradleKotlinDsl) {
                buildTool = BuildTool.GRADLE_KOTLIN_DSL;
            } else if (gradle) {
                buildTool = BuildTool.GRADLE;
            }

            output.info("Creating Roq site: " + name);
            output.info("Extensions: " + extensions);

            QuarkusProject qp = QuarkusProjectHelper.getProject(projectDir, buildTool);

            CreateProject createProject = new CreateProject(qp)
                    .groupId(groupId)
                    .artifactId(name)
                    .version(version)
                    .extensions(extensions);

            if (noCode) {
                createProject.noCode();
            }

            QuarkusCommandOutcome outcome = createProject.execute();

            if (outcome.isSuccess()) {
                // Move application.properties to config/
                Path propsSource = projectDir.resolve("src/main/resources/application.properties");
                Path configDir = projectDir.resolve("config");
                Files.createDirectories(configDir);
                Path propsDest = configDir.resolve("application.properties");
                if (Files.exists(propsSource)) {
                    Files.move(propsSource, propsDest);
                } else {
                    Files.createFile(propsDest);
                }

                // Remove src/ and docker files (not needed for a Roq site)
                deleteDir(projectDir.resolve("src"));
                Files.deleteIfExists(projectDir.resolve(".dockerignore"));

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

    private void deleteDir(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (Exception e) {
                                output.warn("Could not delete: " + p);
                            }
                        });
            }
        }
    }

    private String withVersion(String gav) {
        if (roqVersion != null && gav.startsWith(ROQ_GROUP_ID + ":")) {
            return gav + ":" + roqVersion;
        }
        return gav;
    }

    private String resolveExtension(String value) {
        if (value.contains(":")) {
            return value; // Already a full GAV
        }
        if (value.startsWith("theme:")) {
            return ROQ_PREFIX + "theme-" + value.substring(6);
        }
        if (value.startsWith("plugin:")) {
            return ROQ_PREFIX + "plugin-" + value.substring(7);
        }
        if (value.startsWith("web:")) {
            return "io.quarkiverse.web-bundler:quarkus-web-bundler-" + value.substring(4);
        }
        return ROQ_PREFIX + value;
    }
}
