package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import io.quarkus.cli.common.OutputOptionMixin;
import io.quarkus.cli.common.OutputProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "roq", mixinStandardHelpOptions = true, subcommands = { StartCommand.class, ServeCommand.class,
        CreateCommand.class, UpdateCommand.class, GenerateCommand.class, BlogCommand.class })
public class RoqMain implements Callable<Integer>, OutputProvider {

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @Override
    public OutputOptionMixin getOutput() {
        return output;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RoqMain()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        if (isRoqProject()) {
            return new CommandLine(this).execute("start");
        }
        new CommandLine(this).usage(System.out);
        return CommandLine.ExitCode.OK;
    }

    static boolean isRoqProject() {
        Path dir = Paths.get(System.getProperty("user.dir"));
        for (String buildFile : new String[] { "pom.xml", "build.gradle", "build.gradle.kts" }) {
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
