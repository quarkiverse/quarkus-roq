package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Moves a Gatsby site's plain CSS to {@code web/}, where Roq's web bundler picks it up, and reports the styles it
 * cannot move: npm packages, CSS modules and styled-components.
 */
final class GatsbyStyles {

    static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:[^\"']+\\s+from\\s+)?[\"']([^\"']+)[\"']");

    private GatsbyStyles() {
    }

    static void convert(GatsbyProject project, TargetSite site) throws IOException {
        Path dir = site.dir();
        Path src = dir.resolve(project.path("src"));

        // Global styles, in the order gatsby-browser.js imports them, then component styles
        List<Path> styles = new ArrayList<>();
        Set<String> packages = new LinkedHashSet<>();
        Path browser = dir.resolve(project.path("gatsby-browser.js"));
        if (Files.exists(browser)) {
            Matcher imported = IMPORT.matcher(Files.readString(browser));
            while (imported.find()) {
                String module = imported.group(1);
                if (module.startsWith(".") && module.endsWith(".css")) {
                    styles.add(browser.getParent().resolve(module).normalize());
                } else if (!module.startsWith(".")) {
                    packages.add(npmPackage(module));
                }
            }
        }
        for (String npm : packages) {
            site.report().todo("Add the npm package " + npm + " (imported in " + project.path("gatsby-browser.js")
                    + ") as an org.mvnpm dependency, and import what the site uses from it in web/main.js");
        }
        for (Path css : SiteFiles.files(src, p -> p.toString().endsWith(".css"))) {
            if (css.toString().endsWith(".module.css")) {
                site.report().todo("Convert the CSS module " + dir.relativize(css) + " to plain CSS in web/; "
                        + "its class names were scoped to a component");
            } else if (!styles.contains(css)) {
                styles.add(css);
            }
        }

        Path web = dir.resolve("web");
        Files.createDirectories(web);
        List<String> imports = new ArrayList<>();
        for (Path css : styles) {
            if (Files.exists(css)) {
                String partial = "_" + css.getFileName();
                Files.copy(css, web.resolve(partial));
                imports.add("import './" + partial + "';");
            }
        }
        if (!imports.isEmpty()) {
            Files.writeString(web.resolve("main.js"), String.join("\n", imports) + "\n");
            site.report().info("Copied " + imports.size() + " stylesheets to web/ and imported them from web/main.js");
        }
        if (!SiteFiles.files(src, GatsbyStyles::usesStyledComponents).isEmpty()) {
            site.report().todo("Move the styles written with styled-components into CSS in web/");
        }
    }

    /**
     * @return the package an import comes from: {@code @fontsource/open-sans/300.css} is {@code @fontsource/open-sans}
     */
    static String npmPackage(String module) {
        String[] parts = module.split("/");
        return module.startsWith("@") && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
    }

    private static boolean usesStyledComponents(Path file) {
        try {
            return GatsbyProject.isReactSource(file) && Files.readString(file).contains("styled-components");
        } catch (IOException e) {
            return false;
        }
    }
}
