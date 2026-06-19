///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2

package io.quarkus.tools.migration.jekyll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "jekyll-frontmatter", mixinStandardHelpOptions = true, version = "1.0",
        description = "Converts Jekyll frontmatter (pagination, permalinks) to Roq equivalents")
public class JekyllFrontMatterCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Jekyll project directory")
    private Path projectDir;

    private JekyllFrontMatterConverter converter = new JekyllFrontMatterConverter();

    public static void main(String... args) {
        int exitCode = new CommandLine(new JekyllFrontMatterCommand()).execute(args);
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

        try {
            converter.convertProject(projectDir);
            System.out.println("✓ Frontmatter conversion complete");
            return 0;
        } catch (Exception e) {
            System.err.println("✗ Error converting frontmatter: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
