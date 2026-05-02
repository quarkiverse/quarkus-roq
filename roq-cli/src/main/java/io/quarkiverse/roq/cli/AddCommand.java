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
            System.err.println("  e.g. roq add tagging sitemap");
            System.err.println("  e.g. roq add theme:resume");
            System.err.println("  e.g. roq add web:sass");
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