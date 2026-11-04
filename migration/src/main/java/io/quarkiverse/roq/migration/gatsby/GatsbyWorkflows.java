package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Reports the GitHub workflows a Gatsby migration breaks: those that build the Gatsby project, and those that use the
 * directories whose pages moved to {@code content/}.
 */
final class GatsbyWorkflows {

    static final Pattern NPM_RUN = Pattern.compile("npm run ([\\w:.-]+)");

    private GatsbyWorkflows() {
    }

    /**
     * @param testScripts the npm scripts that run the tests kept in {@value GatsbySiteTests#DIR}/
     */
    static void check(GatsbyProject project, TargetSite site, List<String> testScripts) throws IOException {
        Path dir = site.dir();
        for (Path workflow : SiteFiles.files(dir.resolve(".github/workflows"), p -> p.toString().matches(".*\\.ya?ml"))) {
            String text = Files.readString(workflow);
            String file = dir.relativize(workflow).toString();
            if (!project.root().isEmpty() && text.contains(project.root() + "/")) {
                site.report().todo("Update " + file + ": it builds the Gatsby site in " + project.root()
                        + "/; the site is now built with Maven (QUARKUS_ROQ_GENERATOR_BATCH=true mvn package "
                        + "quarkus:run) and generated in target/roq/");
            }
            List<String> moved = new ArrayList<>();
            for (String sourceDir : project.markdownSources()) {
                if (text.contains(sourceDir + "/") || text.contains(sourceDir + "\n")
                        || text.contains("'" + sourceDir + "'")) {
                    moved.add(sourceDir + "/");
                }
            }
            Matcher npmRun = NPM_RUN.matcher(text);
            while (npmRun.find()) {
                if (testScripts.contains(npmRun.group(1))) {
                    site.report().todo("Update " + file + ": run 'npm run " + npmRun.group(1) + "' from "
                            + GatsbySiteTests.DIR + "/, after building the site with Maven");
                }
            }
            if (!moved.isEmpty()) {
                site.report().todo("Update " + file + ": it uses " + String.join(", ", moved)
                        + ", which moved unchanged to content/; point it at content/");
            }
        }
    }
}
