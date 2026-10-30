package io.quarkiverse.roq.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import io.quarkus.cli.common.OutputOptionMixin;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "theme", mixinStandardHelpOptions = true, description = "Develop a Roq theme", subcommands = {
        ThemeCommand.Init.class })
public class ThemeCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return CommandLine.ExitCode.OK;
    }

    @Command(name = "init", mixinStandardHelpOptions = true, description = {
            "Scaffold a theme packaged as a jar for other sites.",
            "In a Roq site, the theme is added to the site. Elsewhere, a new preview site is created for the theme." })
    static class Init implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "Theme name (e.g. my-theme)")
        String name;

        @CommandLine.Option(names = { "-d", "--dir" }, description = "Theme directory (default: ${DEFAULT-VALUE})")
        String dir = ThemeInitializer.DEFAULT_DIR;

        @CommandLine.Option(names = { "-g",
                "--group-id" }, description = "Maven group ID of the theme jar (default: ${DEFAULT-VALUE})")
        String groupId = "io.acme";

        @CommandLine.Option(names = {
                "--roq-version" }, description = "Roq version of the preview site, when creating one (default: latest)")
        String roqVersion;

        @CommandLine.Mixin(name = "output")
        OutputOptionMixin output;

        @Override
        public Integer call() {
            // Same resolution as the Quarkus CLI: --cli-test-dir in tests, the working directory otherwise
            Path cwd = output.getTestDirectory();
            if (cwd == null) {
                cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            }
            boolean newSite = !RoqMain.isRoqProject(cwd);
            Path siteDir = newSite ? cwd.resolve(name) : cwd;
            ThemeInitializer.Result result;
            try {
                output.info("\uD83C\uDFA8 Creating Roq theme: " + name + "...");
                result = newSite
                        ? ThemeInitializer.initWithPreviewSite(siteDir, name, dir, groupId, roqVersion)
                        : ThemeInitializer.init(siteDir, name, dir, groupId);
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.error(e.getMessage());
                return CommandLine.ExitCode.USAGE;
            } catch (Exception e) {
                return output.handleCommandException(e, "Unable to create theme: " + e.getMessage());
            }

            Path themePom = Path.of(dir, "pom.xml");
            Path resources = ThemeInitializer.resourcesDir(dir);
            output.info("");
            if (newSite) {
                output.info("\u2728 Roq theme created with a preview site in ./" + Path.of(name));
                output.info("");
                output.info("  Preview the theme:");
                output.info("  \u276F cd " + Path.of(name));
                output.info("  \u276F roq start");
                output.info("");
                output.info("  Edit the theme in " + resources + " (layouts in " + ThemeInitializer.layoutsDir(name)
                        + ", styles in " + ThemeInitializer.WEB_APP + ")");
            } else {
                output.info("\u2728 Roq theme created in ./" + Path.of(dir));
                if (result.pomUpdated()) {
                    output.info("  Added " + resources + " to the site resources in pom.xml");
                }
                if (result.altExprSyntax()) {
                    output.info("  Enabled the alternative expression syntax for the packaged jar (templates/.qute)");
                }
                output.info("");
                output.info("  Next, move the theme files from the site:");
                output.info("  - partials to " + resources.resolve(ThemeInitializer.partialsDir(name)));
                output.info("  - layouts to " + resources.resolve(ThemeInitializer.layoutsDir(name)));
                output.info("  - styles and scripts to " + resources.resolve(ThemeInitializer.WEB_APP));
                output.info("  Once the theme has layouts, set 'site.theme=" + name + "' in the site configuration.");
            }
            output.info("");
            output.info("  Package the theme for other sites:");
            output.info("  \u276F mvn -f " + themePom + " install");
            output.info("");
            return CommandLine.ExitCode.OK;
        }
    }
}
