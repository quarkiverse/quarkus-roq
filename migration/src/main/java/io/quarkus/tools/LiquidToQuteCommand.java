///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private List<String> templateExtensions = List.of(".html", ".htm", ".liquid", ".md", ".markdown", ".xml");

    @Option(names = {
            "--extension-syntax" }, description = "Use Qute extension syntax {=expr} instead of standard {expr} (default: true)", defaultValue = "true", negatable = true)
    private boolean extensionSyntax = true;

    @Option(names = { "--partials" }, description = "Converting partials/includes (uses {page.content} instead of {#insert /})")
    private boolean partials;

    @Option(names = {
            "--config-mappings" }, split = ",", description = "ConfigMapping section names from _config.yml (e.g. search,analytics)")
    private List<String> configMappings;

    private LiquidToQuteConverter converter = new LiquidToQuteConverter();

    public static void main(String... args) {
        int exitCode = new CommandLine(new LiquidToQuteCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        converter = new LiquidToQuteConverter(extensionSyntax);
        converter.setConvertingPartials(partials);
        if (configMappings != null && !configMappings.isEmpty()) {
            converter.setConfigMappingSections(configMappings);
        }

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

        // Post-process: fix cross-file scoping issues where a parent template
        // includes a partial that sets a variable via the (now-collapsed) push-in-loop pattern.
        // The parent's {#if values}/{#for guide in values} must use mergeTypes() directly.
        fixMergeIncludeCallers(outputDir);

        System.out.println("\nConversion complete:");
        System.out.println("  ✓ " + convertedCount + " files converted");
        if (errorCount > 0) {
            System.out.println("  ✗ " + errorCount + " files failed");
        }
    }

    /**
     * Finds partials whose push-in-loop was collapsed to mergeTypes(), then fixes parent
     * templates that include them. The parent's {#if values}/{#for guide in values} references
     * a variable scoped inside the include — invisible to the parent in Qute.
     *
     * Auto-detects: scans converted partials for {#let ACCUM=SOURCE.mergeTypes(TYPE_VAR)},
     * then finds parents that {#include} those partials with type="X" and replaces cross-scope
     * references to ACCUM with direct SOURCE.mergeTypes('X') calls.
     */
    private void fixMergeIncludeCallers(Path outputDir) {
        try {
            // Step 1: find partials that contain mergeTypes() and extract their metadata
            record MergePartial(String fileName, String accumVar, String sourceVar) {
            }
            List<MergePartial> mergePartials = new ArrayList<>();

            java.util.regex.Pattern mergePattern = java.util.regex.Pattern.compile(
                    "\\{#let (\\w+)=(\\w[\\w.]*)\\.mergeTypes\\(");

            try (Stream<Path> paths = Files.walk(outputDir)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".html"))
                        .toList()) {
                    String content = Files.readString(file);
                    java.util.regex.Matcher m = mergePattern.matcher(content);
                    if (m.find()) {
                        String fileName = file.getFileName().toString();
                        mergePartials.add(new MergePartial(fileName, m.group(1), m.group(2)));
                    }
                }
            }

            if (mergePartials.isEmpty())
                return;

            // Step 2: fix parent templates that include these partials
            try (Stream<Path> paths = Files.walk(outputDir)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".html"))
                        .toList()) {
                    String content = Files.readString(file);
                    String original = content;

                    for (MergePartial partial : mergePartials) {
                        if (!content.contains(partial.fileName()))
                            continue;

                        String fnQuoted = java.util.regex.Pattern.quote(partial.fileName());

                        // Extract types from include lines — match any path ending with the filename
                        java.util.regex.Pattern includeP = java.util.regex.Pattern.compile(
                                "\\{#include \\S*" + fnQuoted + " type=\"([^\"]+)\" /\\}");
                        java.util.regex.Matcher includeM = includeP.matcher(content);
                        List<String> types = new ArrayList<>();
                        while (includeM.find()) {
                            types.add(includeM.group(1));
                        }
                        if (types.isEmpty())
                            continue;

                        // Replace each include + {#if ACCUM} with {#if SOURCE.mergeTypes('TYPE')}
                        for (String type : types) {
                            content = content.replaceFirst(
                                    "\\{#include \\S*" + fnQuoted + " type=\""
                                            + java.util.regex.Pattern.quote(type) + "\" /\\}"
                                            + "\\s*\n(\\s*)\\{#if " + java.util.regex.Pattern.quote(partial.accumVar()) + "\\}",
                                    "$1{#if " + partial.sourceVar() + ".mergeTypes('" + type + "')}");
                        }

                        // Replace {#for VAR in ACCUM.orEmpty} with type-specific mergeTypes calls
                        for (String type : types) {
                            content = content.replaceFirst(
                                    "\\{#for (\\w+) in " + java.util.regex.Pattern.quote(partial.accumVar()) + "\\.orEmpty\\}",
                                    "{#for $1 in " + partial.sourceVar() + ".mergeTypes('" + type + "')}");
                        }
                    }

                    if (!content.equals(original)) {
                        Files.writeString(file, content);
                        if (verbose) {
                            System.out.println("  Post-processed: " + file + " (fixed merge include callers)");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: post-processing failed: " + e.getMessage());
        }
    }
}
