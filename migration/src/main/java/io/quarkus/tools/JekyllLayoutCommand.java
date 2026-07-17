///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "jekyll-layout", mixinStandardHelpOptions = true, version = "1.0", description = "Converts Jekyll layout HTML to Roq layout HTML")
public class JekyllLayoutCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Layout file to convert (e.g., _layouts/base.html)")
    private Path inputFile;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(inputFile)) {
            System.err.println("Error: File not found: " + inputFile);
            return 1;
        }

        if (!Files.isRegularFile(inputFile)) {
            System.err.println("Error: Not a regular file: " + inputFile);
            return 1;
        }

        JekyllLayoutConverter converter = new JekyllLayoutConverter();

        try {
            String content = Files.readString(inputFile);
            String converted = converter.replaceAssetsCssWithBundle(content);
            Files.writeString(inputFile, converted);
            System.out.println("Converted: " + inputFile);
            return 0;
        } catch (IOException e) {
            System.err.println("Error converting file: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new JekyllLayoutCommand()).execute(args);
        System.exit(exitCode);
    }
}
