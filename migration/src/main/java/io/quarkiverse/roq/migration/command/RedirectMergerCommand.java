///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2

package io.quarkiverse.roq.migration.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.quarkiverse.roq.migration.RedirectMerger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Merges standalone redirect files into their target files as aliases; see {@link RedirectMerger}.
 */
@Command(name = "merge-redirects", mixinStandardHelpOptions = true, version = "1.0", description = "Merge redirect files into target files as aliases")
public class RedirectMergerCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project directory (must contain content/)")
    private Path projectDir;

    public static void main(String... args) {
        int exitCode = new CommandLine(new RedirectMergerCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Path contentDir = projectDir.resolve("content");

        if (!Files.isDirectory(contentDir.resolve("redirects"))) {
            System.out.println("No redirects directory found - nothing to merge");
            return 0;
        }

        int mergedCount = new RedirectMerger().merge(contentDir);

        System.out.println("Merged " + mergedCount + " redirect(s) into target files");
        return 0;
    }
}
