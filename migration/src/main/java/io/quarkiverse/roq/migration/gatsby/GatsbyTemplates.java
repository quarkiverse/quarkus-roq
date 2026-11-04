package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkiverse.roq.migration.engine.MigrationReport;
import io.quarkiverse.roq.migration.engine.TargetSite;
import io.quarkiverse.roq.migration.react.JsxToQuteConverter;
import io.quarkiverse.roq.migration.react.ReactComponent;

/**
 * Writes the Roq layouts of a migrated Gatsby site, with the React components that could be converted as Qute
 * partials, and reports what to do with the others according to what kind of component each is.
 * <p>
 * The main layout's {@code <body>} is the site's root layout component when that could be converted. Otherwise it is
 * a generic one, with a navigation of all the pages, that renders the converted components the root layout rendered
 * around its content.
 * <p>
 * The layouts are Qute templates already, so they are copied from {@code templates/gatsby/layouts/} as they are,
 * apart from their placeholders: {@code ${body}}, and in the generic body {@code ${notHome}}, {@code ${header}} and
 * {@code ${footer}}.
 */
final class GatsbyTemplates {

    private GatsbyTemplates() {
    }

    /**
     * @param homePage the home page relative to {@code content/}, or null
     */
    static void convert(GatsbyProject project, TargetSite site, String homePage, GatsbyComponents components)
            throws IOException {
        MigrationReport report = site.report();
        Path templates = site.dir().resolve("templates");
        Path layouts = templates.resolve("layouts");
        Files.createDirectories(layouts);

        for (GatsbyComponents.Outcome outcome : components.outcomes().values()) {
            if (outcome.converted() != null && !outcome.template().equals(GatsbyComponents.MAIN_LAYOUT)) {
                Path partial = templates.resolve(outcome.template() + ".html");
                Files.createDirectories(partial.getParent());
                Files.writeString(partial, outcome.converted().template());
                report.info("Converted " + outcome.component().file() + " to templates/" + outcome.template() + ".html");
            }
        }

        String body;
        if (components.layoutConverted()) {
            body = components.outcomes().get(components.rootLayout()).converted().template();
            report.info("Converted " + components.rootLayout() + " to the body of templates/layouts/main.html");
        } else {
            // The converted components the site's layout renders around its content, in the same places
            String header = components.before().isEmpty() ? layout("header.html") : includes(components.before());
            String footer = includes(components.after());
            // The home page is also shown at /, so the navigation leaves it out
            String notHome = homePage == null || homePage.equals("index.md") ? ""
                    : " && !p.sourcePath.equals('" + homePage + "')";
            body = layout("main-body.html")
                    .replace("${notHome}", notHome)
                    .replace("${header}", header)
                    .replace("${footer}", footer);
        }
        Files.writeString(layouts.resolve("main.html"), layout("main.html").replace("${body}", indented(body)));
        Files.writeString(layouts.resolve("page.html"), layout("page.html"));
        report.info("Wrote templates/layouts/main.html and page.html; pages without a title get one from their file name");
        if (components.layoutConverted()) {
            reportLayoutProps(components, report);
        } else {
            reportWiring(components, report);
        }
        components.guessedFields().forEach((file, fields) -> report.todo("Check the titles in templates/"
                + components.outcomes().get(file).template() + ".html: " + file + " used " + String.join(", ", fields)
                + ", which the site's gatsby-node.js or a plugin set; the template uses the front matter title, or "
                + "else the file name with dashes as spaces"));

        for (GatsbyComponents.Outcome outcome : components.outcomes().values()) {
            reportOutcome(outcome, components, report);
        }
    }

    /**
     * The root layout's props, other than its content, came from the pages; a Roq layout only has the page and the
     * site.
     */
    private static void reportLayoutProps(GatsbyComponents components, MigrationReport report) {
        ReactComponent layout = components.outcomes().get(components.rootLayout()).component();
        for (String prop : layout.props()) {
            if (!prop.equals("children")) {
                report.todo("Give " + prop + " a value in templates/layouts/main.html, for example from page with "
                        + "{#let}: the pages passed it to " + layout.file() + ", and a Roq layout only has page and "
                        + "site");
            }
        }
    }

    private static void reportWiring(GatsbyComponents components, MigrationReport report) {
        for (JsxToQuteConverter.Include include : wired(components)) {
            report.info("templates/layouts/main.html renders " + include.include() + ", as "
                    + components.rootLayout() + " did");
            for (String prop : include.omitted()) {
                report.todo("Pass " + prop + " to " + include.include().replaceAll("^\\{#include ([^\\s}]+).*", "$1")
                        + " in templates/layouts/main.html: " + components.rootLayout() + " passes it from its own "
                        + "props, which have no Roq equivalent");
            }
        }
    }

    private static void reportOutcome(GatsbyComponents.Outcome outcome, GatsbyComponents components,
            MigrationReport report) {
        ReactComponent component = outcome.component();
        String file = component.file();
        String name = file.substring(file.lastIndexOf('/') + 1);
        if (outcome.page()) {
            if (name.startsWith("{")) {
                report.info(file + " renders the Markdown pages; Roq does that with templates/layouts/page.html");
            } else if (name.matches("index\\.(jsx?|tsx?)")) {
                report.todo("Port the home page " + file + " to templates/layouts/main.html or content/index.md"
                        + kindNote(component));
            } else {
                report.todo("Recreate the page " + file + " as content/" + name.replaceAll("\\.[^.]+$", "") + ".html"
                        + kindNote(component));
            }
            return;
        }
        if (outcome.converted() != null) {
            if (outcome.template().equals(GatsbyComponents.MAIN_LAYOUT)) {
                return;
            }
            String include = "{#include " + outcome.template();
            boolean used = wired(components).stream().anyMatch(i -> includes(i.include(), include))
                    || components.outcomes().values().stream()
                            .anyMatch(o -> o.converted() != null && includes(o.converted().template(), include));
            if (!used) {
                report.todo("Include templates/" + outcome.template() + ".html, converted from " + file
                        + ", where the site needs it: " + include + " /}");
            }
            return;
        }
        String reasons = String.join(", ", component.reasons());
        switch (component.kind()) {
            case STATIC -> report.todo("Port the React component " + file + " to Qute in templates/: it is a static "
                    + "template, but it could not be converted automatically because " + outcome.reason());
            case SIDE_EFFECTS -> report.todo("Port the React component " + file + " to Qute in templates/, and move "
                    + "its " + reasons + " to the layout; it could not be converted automatically because "
                    + outcome.reason());
            case INTERACTIVE -> report.todo("Keep the React component " + file + " as React, or rewrite it: it is "
                    + "interactive, with " + reasons + ", which a Qute template is not");
            case COMPUTED_IN_BROWSER -> report.todo("Move what the React component " + file + " computes in the "
                    + "browser (" + reasons + ") to build time, with Roq data or Qute, then port it to Qute in templates/");
        }
    }

    private static boolean includes(String template, String include) {
        return template.contains(include + " ") || template.contains(include + "}");
    }

    private static String kindNote(ReactComponent component) {
        String reasons = String.join(", ", component.reasons());
        return switch (component.kind()) {
            case STATIC -> "; it is a static template";
            case SIDE_EFFECTS -> "; it is a static template with " + reasons;
            case INTERACTIVE -> "; it is interactive, with " + reasons;
            case COMPUTED_IN_BROWSER -> "; it computes what it renders in the browser (" + reasons + ")";
        };
    }

    private static List<JsxToQuteConverter.Include> wired(GatsbyComponents components) {
        return Stream.concat(components.before().stream(), components.after().stream()).toList();
    }

    private static String includes(List<JsxToQuteConverter.Include> includes) {
        return includes.stream().map(JsxToQuteConverter.Include::include).collect(Collectors.joining("\n"));
    }

    /**
     * @return the body indented to go in {@code <body>}, unless it has preformatted text, whose spaces would show
     */
    private static String indented(String body) {
        String b = body.stripTrailing();
        if (b.contains("<pre") || b.contains("<textarea")) {
            return b;
        }
        return b.lines().map(line -> line.isBlank() ? "" : "  " + line).collect(Collectors.joining("\n"));
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
