package io.quarkiverse.roq.cli;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.cli.common.BuildToolContext;
import io.quarkus.cli.common.BuildToolDelegatingCommand;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "add", mixinStandardHelpOptions = true, description = "Add a Roq plugin, theme, or extension")
public class AddCommand extends BuildToolDelegatingCommand {

    @Override
    public void populateContext(BuildToolContext context) {
        RoqMain.requireRoqProject("add");
        List<String> params = context.getParams();
        if (params.isEmpty()) {
            System.err.println("Usage: roq add <extension> [extension...]");
            System.err.println("");
            System.err.println("  Prefixes:");
            System.err.println("    plugin:<name>   Roq plugin (e.g. tagging, sitemap, aliases, series)");
            System.err.println("    theme:<name>    Roq theme (e.g. default, resume, base)");
            System.err.println("    web:<name>      Web Bundler extension (e.g. tailwindcss)");
            System.err.println("    <name>          Any Quarkus extension (e.g. rest-jackson, hibernate-orm)");
            System.err.println("    <group:artifact> Full GAV coordinate");
            System.err.println("");
            System.err.println("  Examples:");
            System.err.println("    roq add plugin:tagging plugin:sitemap");
            System.err.println("    roq add theme:resume");
            System.err.println("    roq add web:tailwindcss");
            System.err.println("    roq add rest-jackson");
            System.exit(CommandLine.ExitCode.USAGE);
        }
        String resolved = params.stream()
                .map(RoqProjectCreator::resolveExtension)
                .collect(Collectors.joining(","));
        context.getPropertiesOptions().properties.put("extensions", resolved);
        params.clear();
    }

    @Override
    public void prepareMaven(BuildToolContext context) {
        // No pre-compilation needed
    }

    @Override
    public Map<BuildTool, String> getActionMapping() {
        return Map.of(
                BuildTool.MAVEN, "quarkus:add-extension",
                BuildTool.GRADLE, "quarkusAddExtension",
                BuildTool.GRADLE_KOTLIN_DSL, "quarkusAddExtension");
    }
}