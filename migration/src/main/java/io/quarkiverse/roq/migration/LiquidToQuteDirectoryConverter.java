package io.quarkiverse.roq.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts Liquid template files, or whole directories of them, to Qute with a {@link LiquidToQuteConverter}.
 */
public class LiquidToQuteDirectoryConverter {

    public static final List<String> DEFAULT_TEMPLATE_EXTENSIONS = List.of(".html", ".htm", ".liquid", ".md",
            ".markdown", ".xml");

    public record Result(int converted, int failed) {
    }

    private final LiquidToQuteConverter converter;
    private List<String> templateExtensions = DEFAULT_TEMPLATE_EXTENSIONS;
    private boolean recursive = true;
    private boolean verbose;

    public LiquidToQuteDirectoryConverter(LiquidToQuteConverter converter) {
        this.converter = converter;
    }

    /**
     * @param templateExtensions file name endings of the files to convert in a directory; others are skipped
     */
    public LiquidToQuteDirectoryConverter templateExtensions(List<String> templateExtensions) {
        this.templateExtensions = templateExtensions;
        return this;
    }

    public LiquidToQuteDirectoryConverter recursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    /**
     * @param verbose print each converted file and its conversion report
     */
    public LiquidToQuteDirectoryConverter verbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * @return false if the file could not be converted; the error has been printed
     */
    public boolean convertFile(Path inputPath, Path outputPath) {
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

    /**
     * Convert every template under a directory, keeping the relative layout, then fix include callers of partials
     * whose push-in-loop was collapsed (see {@link #fixMergeIncludeCallers(Path)}).
     */
    public Result convertDirectory(Path inputDir, Path outputDir) throws IOException {
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

        return new Result(convertedCount, errorCount);
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
                                "\\{#include \\S*" + fnQuoted + " type=\"([^\"]+)\"(?: _unisolated)? /\\}");
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
                                            + java.util.regex.Pattern.quote(type) + "\"(?: _unisolated)? /\\}"
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
