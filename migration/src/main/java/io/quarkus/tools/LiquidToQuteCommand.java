///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "liquid-to-qute", mixinStandardHelpOptions = true, version = "1.0", description = "Converts Liquid templates to Qute templates for Roq")
public class LiquidToQuteCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file or directory")
    private Path input;

    @Parameters(index = "1", description = "Output file or directory (optional for single files)", arity = "0..1")
    private Path output;

    @Option(names = { "-r",
            "--recursive" }, description = "Process directories recursively", defaultValue = "true", negatable = true)
    private boolean recursive;

    @Option(names = { "-v", "--verbose" }, description = "Verbose output")
    private boolean verbose;

    @Option(names = { "-e",
            "--extensions" }, description = "Template file extensions to process (default: .html, .htm, .liquid, .md, .markdown)", split = ",")
    private List<String> templateExtensions = List.of(".html", ".htm", ".liquid", ".md", ".markdown");

    @Option(names = {
            "--extension-syntax" }, description = "Use Qute extension syntax {=expr} instead of standard {expr} (default: true)", defaultValue = "true", negatable = true)
    private boolean extensionSyntax = true;

    @Option(names = {
            "--partials" }, description = "Converting partials/includes (uses {page.content} instead of {#insert /})", defaultValue = "true", negatable = true)
    private boolean partials;

    private LiquidToQuteConverter converter = new LiquidToQuteConverter();

    public static void main(String... args) {
        int exitCode = new CommandLine(new LiquidToQuteCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        converter = new LiquidToQuteConverter(extensionSyntax);
        converter.setConvertingPartials(partials);

        if (!Files.exists(input)) {
            System.err.println("Error: Input path '" + input + "' does not exist");
            return 1;
        }

        if (Files.isRegularFile(input)) {
            return convertFile(input, getOutputPath(input)) ? 0 : 1;
        }

        if (Files.isDirectory(input)) {
            if (output == null) {
                System.err.println("Error: Output directory required for directory conversion");
                return 1;
            }
            convertDirectory(input, output);
            return 0;
        }

        System.err.println("Error: '" + input + "' is neither a file nor a directory");
        return 1;
    }

    private Path getOutputPath(Path inputPath) {
        if (output != null) {
            return output;
        }
        // Default: add .qute before extension
        String fileName = inputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = fileName.substring(0, dotIndex);
            String ext = fileName.substring(dotIndex);
            return inputPath.getParent().resolve(name + ".qute" + ext);
        }
        return inputPath.getParent().resolve(fileName + ".qute");
    }

    boolean convertFile(Path inputPath, Path outputPath) {
        try {
            String content = Files.readString(inputPath);
            converter.clearConversions();

            String converted = converter.convert(content);

            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, converted);

            if (verbose) {
                System.out.println("✓ Converted: " + inputPath + " -> " + outputPath);
                System.out.println(converter.getConversionReport());
                System.out.println();
            }

            return true;
        } catch (IOException e) {
            System.err.println("✗ Error converting " + inputPath + ": " + e.getMessage());
            return false;
        }
    }

    private void convertDirectory(Path inputDir, Path outputDir) throws IOException {
        int convertedCount = 0;
        int errorCount = 0;

        try (Stream<Path> paths = recursive ? Files.walk(inputDir) : Files.list(inputDir)) {
            for (Path inputPath : paths.filter(Files::isRegularFile).toList()) {
                String fileName = inputPath.getFileName().toString();
                boolean isTemplate = templateExtensions.stream()
                        .anyMatch(fileName::endsWith);

                if (isTemplate) {
                    Path relativePath = inputDir.relativize(inputPath);
                    Path outputPath = outputDir.resolve(relativePath);

                    if (convertFile(inputPath, outputPath)) {
                        convertedCount++;
                    } else {
                        errorCount++;
                    }
                }
            }
        }

        System.out.println("\nConversion complete:");
        System.out.println("  ✓ " + convertedCount + " files converted");
        if (errorCount > 0) {
            System.out.println("  ✗ " + errorCount + " files failed");
        }
    }
}
