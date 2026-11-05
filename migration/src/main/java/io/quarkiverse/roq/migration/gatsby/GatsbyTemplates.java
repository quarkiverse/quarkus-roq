package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Writes minimal Roq layouts for a migrated Gatsby site, and reports the React components and pages to port.
 * <p>
 * The layouts are Qute templates already, so they are copied from {@code templates/gatsby/layouts/} as they are, apart
 * from the {@code ${notHome}} placeholder.
 */
final class GatsbyTemplates {

    private GatsbyTemplates() {
    }

    /**
     * @param homePage the home page relative to {@code content/}, or null
     */
    static void convert(GatsbyProject project, TargetSite site, String homePage) throws IOException {
        Path layouts = site.dir().resolve("templates/layouts");
        Files.createDirectories(layouts);
        // The home page is also shown at /, so the navigation leaves it out
        String notHome = homePage == null || homePage.equals("index.md") ? ""
                : " && !p.sourcePath.equals('" + homePage + "')";
        Files.writeString(layouts.resolve("main.html"), layout("main.html").replace("${notHome}", notHome));
        Files.writeString(layouts.resolve("page.html"), layout("page.html"));
        site.report().info("Wrote a minimal templates/layouts/main.html and page.html, so the site can be browsed; "
                + "pages without a title get one from their file name");

        Map<String, Set<String>> replaced = GatsbyRoutes.replacedByRoq(project, site.dir());
        for (Path component : SiteFiles.files(site.dir().resolve(project.path("src")), GatsbyProject::isReactSource)) {
            String file = site.dir().relativize(component).toString().replace('\\', '/');
            String name = component.getFileName().toString();
            if (replaced.containsKey(file)) {
                site.report().info(file + " renders the Markdown pages for " + String.join(", ", replaced.get(file))
                        + "; Roq does that with templates/layouts/page.html");
            } else if (file.contains("/pages/") && name.startsWith("{")) {
                site.report().info(file + " renders the Markdown pages; Roq does that with templates/layouts/page.html");
            } else if (file.contains("/pages/") && name.matches("index\\.(jsx?|tsx?)")) {
                site.report().todo("Port the home page " + file + " to templates/layouts/main.html or content/index.md");
            } else if (file.contains("/pages/")) {
                site.report().todo("Recreate the page " + file + " as content/" + name.replaceAll("\\.[^.]+$", "")
                        + ".html");
            } else {
                site.report().todo("Port the React component " + file + " to Qute in templates/");
            }
        }
    }

    static String layout(String name) {
        return resource("layouts/" + name);
    }

    /**
     * @return a file the migration writes into Gatsby sites, from {@code templates/gatsby/} in the resources
     */
    static String resource(String path) {
        try (InputStream in = GatsbyTemplates.class.getResourceAsStream("/templates/gatsby/" + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
