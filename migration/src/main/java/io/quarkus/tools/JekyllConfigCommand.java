///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2

package io.quarkus.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "jekyll-config", mixinStandardHelpOptions = true, version = "1.0",
        description = "Converts Jekyll _config.yml to Roq application.properties and data/siteConfig.yml")
public class JekyllConfigCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Jekyll project directory")
    private Path projectDir;

    private JekyllConfigConverter converter = new JekyllConfigConverter();

    public static void main(String... args) {
        int exitCode = new CommandLine(new JekyllConfigCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(projectDir)) {
            System.err.println("Error: Project directory '" + projectDir + "' does not exist");
            return 1;
        }

        if (!Files.isDirectory(projectDir)) {
            System.err.println("Error: '" + projectDir + "' is not a directory");
            return 1;
        }

        Path configFile = projectDir.resolve("_config.yml");
        if (!Files.exists(configFile)) {
            System.err.println("Error: _config.yml not found in " + projectDir);
            return 1;
        }

        try {
            converter.convertProject(projectDir);
            
            System.out.println("✓ Jekyll config conversion complete:");
            System.out.println("  - Created/updated: config/application.properties");
            System.out.println("  - Created: data/siteConfig.yml");
            
            return 0;
        } catch (Exception e) {
            System.err.println("✗ Error converting Jekyll config: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}