package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkus.cli.common.OutputOptionMixin;
import io.quarkus.cli.common.OutputProvider;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@QuarkusMain
@Command(name = "roq", mixinStandardHelpOptions = true, versionProvider = RoqVersionProvider.class, subcommands = {
        StartCommand.class, ServeCommand.class,
        CreateCommand.class, AddCommand.class, UpdateCommand.class, GenerateCommand.class, BlogCommand.class,
        MigrateCommand.class })
public class RoqMain implements QuarkusApplication, Callable<Integer>, OutputProvider {

    /**
     * The build tools a Roq project can use.
     */
    static final List<BuildTool> BUILD_TOOLS = List.of(BuildTool.MAVEN, BuildTool.GRADLE, BuildTool.GRADLE_KOTLIN_DSL);

    /**
     * The main build file of a project, one per build tool: {@code pom.xml}, {@code build.gradle} or
     * {@code build.gradle.kts}.
     */
    static final List<String> BUILD_FILES = BUILD_TOOLS.stream().map(BuildTool::getDependenciesFile).toList();

    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @CommandLine.Unmatched
    List<String> unmatched = new ArrayList<>();

    @Override
    public OutputOptionMixin getOutput() {
        return output;
    }

    public static void main(String... args) {
        Quarkus.run(RoqMain.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        CommandLine cmd = factory == null ? new CommandLine(this) : new CommandLine(this, factory);
        return cmd.execute(args);
    }

    @Override
    public Integer call() {
        if (isRoqProject()) {
            List<String> args = new ArrayList<>();
            args.add("start");
            args.addAll(unmatched);
            CommandLine cmd = factory == null ? new CommandLine(this) : new CommandLine(this, factory);
            return cmd.execute(args.toArray(new String[0]));
        }
        new CommandLine(this).usage(System.out);
        return CommandLine.ExitCode.OK;
    }

    static boolean isRoqProject() {
        Path dir = Paths.get(System.getProperty("user.dir"));
        for (String buildFile : BUILD_FILES) {
            Path path = dir.resolve(buildFile);
            if (Files.exists(path)) {
                try {
                    String content = Files.readString(path);
                    if (content.contains("quarkus-roq")) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    static void requireRoqProject(String commandName) {
        if (!isRoqProject()) {
            System.err.println("The '" + commandName + "' command must be run from a Roq project directory.");
            System.exit(CommandLine.ExitCode.USAGE);
        }
    }
}
