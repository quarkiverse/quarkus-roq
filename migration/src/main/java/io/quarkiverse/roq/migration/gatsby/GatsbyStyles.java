package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import io.quarkiverse.roq.migration.MvnpmDependencies;
import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Moves a Gatsby site's global styles to {@code web/main.js}, where Roq's web bundler picks them up: plain CSS is
 * copied to {@code web/}, and npm packages such as fonts are imported as before, from the mvnpm dependencies the
 * project was scaffolded with. Reports the styles it cannot move: CSS modules and styled-components.
 */
final class GatsbyStyles {

    private GatsbyStyles() {
    }

    static void convert(GatsbyProject project, TargetSite site) throws IOException {
        Path dir = site.dir();
        Path src = dir.resolve(project.path("src"));
        Path web = dir.resolve("web");
        Files.createDirectories(web);

        // Global styles, in the order gatsby-browser.js imports them, then component styles
        List<String> imports = new ArrayList<>();
        Set<Path> copied = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        Path browser = dir.resolve(project.path("gatsby-browser.js"));
        for (GatsbyProject.BrowserImport browserImport : project.browserImports()) {
            String module = browserImport.module();
            if (browserImport.isLocal()) {
                Path css = browser.getParent().resolve(module).normalize();
                if (module.endsWith(".css") && Files.exists(css) && copied.add(css)) {
                    imports.add(copyToWeb(css, web));
                }
            } else if (browserImport.bindsNames()) {
                // Used by code in gatsby-browser.js, which has no Roq equivalent
                site.report().todo("Port what " + project.path("gatsby-browser.js") + " does with '" + module
                        + "' to a script in web/, if the site still needs it");
            } else {
                String npm = MvnpmDependencies.npmPackage(module);
                if (project.npmVersions().get(npm) == null) {
                    if (packages.add(npm)) {
                        site.report().todo("Add the npm package " + npm + " (imported in "
                                + project.path("gatsby-browser.js") + ") as an org.mvnpm dependency; its version is "
                                + "not in package-lock.json or package.json");
                    }
                } else {
                    // The project depends on it through mvnpm, added when it was scaffolded
                    packages.add(npm);
                    imports.add("import '" + module + "';");
                }
            }
        }
        Set<Path> imported = new LinkedHashSet<>();
        for (Path file : SiteFiles.files(src, GatsbyProject::isReactSource)) {
            Matcher css = GatsbyProject.IMPORT.matcher(Files.readString(file));
            while (css.find()) {
                if (css.group(2).endsWith(".css")) {
                    imported.add(file.getParent().resolve(css.group(2)).normalize());
                }
            }
        }
        for (Path css : SiteFiles.files(src, p -> p.toString().endsWith(".css"))) {
            if (css.toString().endsWith(".module.css")) {
                if (imported.contains(css)) {
                    site.report().todo("Convert the CSS module " + SiteFiles.relativePath(dir, css) + " to plain CSS in web/; "
                            + "its class names were scoped to a component");
                }
            } else if (copied.add(css)) {
                imports.add(copyToWeb(css, web));
            }
        }

        if (!imports.isEmpty()) {
            Files.writeString(web.resolve("main.js"), String.join("\n", imports) + "\n");
            site.report().info("Wrote web/main.js, which imports " + copied.size() + " stylesheets copied to web/"
                    + (packages.isEmpty() ? "" : " and the npm packages " + String.join(", ", packages)));
        }
        // Not in what only the Markdown route uses, which Roq's page layout replaces
        Set<String> replaced = GatsbyRoutes.replacedByRoq(project, site.dir()).keySet();
        if (SiteFiles.files(src, GatsbyStyles::usesStyledComponents).stream()
                .anyMatch(file -> !replaced.contains(SiteFiles.relativePath(site.dir(), file)))) {
            site.report().todo("Move the styles written with styled-components into CSS in web/");
        }
    }

    /**
     * @return the import of the copy, which is a partial (_name.css) so the bundler only includes it through main.js
     */
    private static String copyToWeb(Path css, Path web) throws IOException {
        String partial = "_" + css.getFileName();
        Files.copy(css, web.resolve(partial));
        return "import './" + partial + "';";
    }

    private static boolean usesStyledComponents(Path file) {
        try {
            return GatsbyProject.isReactSource(file) && Files.readString(file).contains("styled-components");
        } catch (IOException e) {
            return false;
        }
    }
}
