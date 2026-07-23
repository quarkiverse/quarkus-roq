///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkus.tools;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "jekyll-plugins", mixinStandardHelpOptions = true, version = "1.0", description = "Converts Jekyll _plugins/*.rb to Roq/Quarkus equivalents")
public class JekyllPluginCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project directory (must contain _plugins/)")
    private java.nio.file.Path projectDir;

    public static void main(String... args) {
        int exitCode = new CommandLine(new JekyllPluginCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!java.nio.file.Files.isDirectory(projectDir)) {
            System.err.println("Error: '" + projectDir + "' is not a directory");
            return 1;
        }

        System.out.println("Converting Jekyll plugins in " + projectDir.resolve("_plugins"));
        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        System.out.println("\nPlugin conversion complete:");
        if (!result.handled().isEmpty()) {
            System.out.println("  " + result.handled().size() + " handled (already covered by converter)");
        }
        if (!result.translated().isEmpty()) {
            System.out.println("  " + result.translated().size() + " translated to Java");
        }
        if (!result.skipped().isEmpty()) {
            System.out.println("  " + result.skipped().size() + " skipped (equivalent already exists)");
        }

        if (!result.failed().isEmpty()) {
            System.err.println("\n  " + result.failed().size() + " plugin(s) need manual migration:");
            for (String plugin : result.failed()) {
                System.err.println("\n" + result.failureMessages().get(plugin));
            }
            return 1;
        }

        return 0;
    }
}
