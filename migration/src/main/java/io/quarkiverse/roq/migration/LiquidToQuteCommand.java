///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5

package io.quarkiverse.roq.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

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
    private List<String> templateExtensions = LiquidToQuteDirectoryConverter.DEFAULT_TEMPLATE_EXTENSIONS;

    @Option(names = {
            "--extension-syntax" }, description = "Use Qute extension syntax {=expr} instead of standard {expr} (default: true)", defaultValue = "true", negatable = true)
    private boolean extensionSyntax = true;

    @Option(names = { "--partials" }, description = "Converting partials/includes (uses {page.content} instead of {#insert /})")
    private boolean partials;

    @Option(names = {
            "--config-mappings" }, split = ",", description = "ConfigMapping section names from _config.yml (e.g. search,analytics)")
    private List<String> configMappings;

    public static void main(String... args) {
        int exitCode = new CommandLine(new LiquidToQuteCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        LiquidToQuteConverter converter = new LiquidToQuteConverter(extensionSyntax);
        converter.setConvertingPartials(partials);
        if (configMappings != null && !configMappings.isEmpty()) {
            converter.setConfigMappingSections(configMappings);
        }
        LiquidToQuteDirectoryConverter files = new LiquidToQuteDirectoryConverter(converter)
                .templateExtensions(templateExtensions)
                .recursive(recursive)
                .verbose(verbose);

        if (!Files.exists(input)) {
            System.err.println("Error: Input path '" + input + "' does not exist");
            return 1;
        }

        if (Files.isRegularFile(input)) {
            return files.convertFile(input, getOutputPath(input)) ? 0 : 1;
        }

        if (Files.isDirectory(input)) {
            if (output == null) {
                System.err.println("Error: Output directory required for directory conversion");
                return 1;
            }
            LiquidToQuteDirectoryConverter.Result result = files.convertDirectory(input, output);
            System.out.println("\nConversion complete:");
            System.out.println("  ✓ " + result.converted() + " files converted");
            if (result.failed() > 0) {
                System.out.println("  ✗ " + result.failed() + " files failed");
            }
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
}
