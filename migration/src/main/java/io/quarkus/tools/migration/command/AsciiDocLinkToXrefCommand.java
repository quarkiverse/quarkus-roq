///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkus.tools.migration.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.quarkus.tools.migration.asciidoc.AsciiDocLinkToXrefConverter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "asciidoc-link-to-xref", mixinStandardHelpOptions = true, version = "1.0", description = "Converts AsciiDoc link: macros to xref: for cross-document references")
public class AsciiDocLinkToXrefCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Content directory (e.g., content/)")
    private Path contentDir;

    private AsciiDocLinkToXrefConverter converter = new AsciiDocLinkToXrefConverter();

    public static void main(String... args) {
        int exitCode = new CommandLine(new AsciiDocLinkToXrefCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(contentDir)) {
            System.err.println("Error: Content directory '" + contentDir + "' does not exist");
            return 1;
        }

        if (!Files.isDirectory(contentDir)) {
            System.err.println("Error: '" + contentDir + "' is not a directory");
            return 1;
        }

        try {
            System.out.println("Converting link: to xref: for cross-document references...");
            converter.convertProject(contentDir);
            System.out.println("✓ Link conversion complete");
            return 0;
        } catch (Exception e) {
            System.err.println("✗ Error converting links: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
