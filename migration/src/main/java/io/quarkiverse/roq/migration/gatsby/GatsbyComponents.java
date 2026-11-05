package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.SourceSite;
import io.quarkiverse.roq.migration.react.ImageSize;
import io.quarkiverse.roq.migration.react.JsxToQuteConverter;
import io.quarkiverse.roq.migration.react.ReactComponent;

/**
 * The React components and pages of a Gatsby site, read during the analyze stage: what kind of component each is,
 * which could be converted to Qute partials, and how the site's layout uses them.
 *
 * @param outcomes what happened to each component and page, by file, in file order
 * @param rootLayout the component pages render their content in, e.g. {@code site/src/components/layout.js}; null
 *        if there is none; when it is converted, it is the site's main layout
 * @param before when the root layout is not converted, the includes of converted components it renders before its
 *        content
 * @param after the includes it renders after its content
 * @param guessedFields the fields of the listed pages that converted components use, by component file, which a
 *        plugin or gatsby-node.js set, and whose Roq equivalent is a guess
 */
record GatsbyComponents(Map<String, Outcome> outcomes, String rootLayout, List<JsxToQuteConverter.Include> before,
        List<JsxToQuteConverter.Include> after, Map<String, Set<String>> guessedFields) {

    /**
     * @param page true for a page in src/pages/, false for a component
     * @param template the template it was converted to, e.g. {@code partials/header}, or {@code layouts/main} for the
     *        root layout; null if it was not
     * @param converted the conversion; null if it was not converted
     * @param reason why it was not converted; null if it was, or for a page
     */
    record Outcome(ReactComponent component, boolean page, String template, JsxToQuteConverter.Converted converted,
            String reason) {
    }

    static final List<String> EXTENSIONS = List.of(".js", ".jsx", ".tsx", ".ts");
    static final String MAIN_LAYOUT = "layouts/main";
    /**
     * A File System Route page for each Markdown page, e.g. {@code {MarkdownRemark.fields__slug}.js}, which serves the
     * pages at that field.
     */
    static final Pattern MARKDOWN_ROUTE = Pattern.compile("\\{markdownRemark\\.(\\w+)}\\.(jsx?|tsx?)",
            Pattern.CASE_INSENSITIVE);
    static final Pattern FRONT_MATTER_FIELD = Pattern.compile("frontmatter\\.(\\w+)");
    /**
     * Gatsby's Markdown pages are the Markdown files of a Roq site.
     */
    static final JsxToQuteConverter.PageList MARKDOWN_PAGES = new JsxToQuteConverter.PageList("site.pages",
            "%s.source.markup == 'markdown'");

    static GatsbyComponents read(SourceSite source, GatsbyProject project) throws MigrationException {
        Map<String, ReactComponent> components = new LinkedHashMap<>();
        for (String file : source.files(project.path("src"))) {
            if (GatsbyProject.isReactSource(Path.of(file))) {
                components.put(file, ReactComponent.read(file, source.readString(file)));
            }
        }
        return new Reader(source, project, components).read();
    }

    /**
     * @return true if the root layout was converted to the site's main layout
     */
    boolean layoutConverted() {
        return rootLayout != null && outcomes.get(rootLayout).converted() != null;
    }

    /**
     * @return true if a converted component has Gatsby images, which need Gatsby's image CSS
     */
    boolean usesImages() {
        return outcomes.values().stream().anyMatch(o -> o.converted() != null && o.converted().images());
    }

    /**
     * @return true if a converted component uses Font Awesome icons
     */
    boolean usesIcons() {
        return outcomes.values().stream().anyMatch(o -> o.converted() != null && o.converted().icons());
    }

    /**
     * @return the CSS of the converted components' styled-components
     */
    String css() {
        StringBuilder css = new StringBuilder();
        outcomes.values().stream().filter(o -> o.converted() != null && !o.converted().css().isEmpty())
                .forEach(o -> css.append(css.isEmpty() ? "" : "\n").append("/* ").append(o.component().file())
                        .append(" */\n").append(o.converted().css()));
        return css.toString();
    }

    private static final class Reader implements JsxToQuteConverter.Context {
        private final SourceSite source;
        private final GatsbyProject project;
        private final Map<String, ReactComponent> components;
        private final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        private final Set<String> converting = new HashSet<>();
        private final Map<String, Set<String>> guessedFields = new LinkedHashMap<>();
        private String rootLayout;
        private String pagePath;

        Reader(SourceSite source, GatsbyProject project, Map<String, ReactComponent> components) {
            this.source = source;
            this.project = project;
            this.components = components;
        }

        GatsbyComponents read() {
            rootLayout = rootLayout();
            pagePath = pagePath();
            for (String file : components.keySet()) {
                outcome(file);
            }
            boolean layoutConverted = rootLayout != null && outcomes.get(rootLayout).converted() != null;
            List<List<JsxToQuteConverter.Include>> around = rootLayout == null || layoutConverted
                    ? List.of(List.of(), List.of())
                    : JsxToQuteConverter.includesAround(components.get(rootLayout), this);
            Map<String, Outcome> sorted = new LinkedHashMap<>();
            components.keySet().forEach(file -> sorted.put(file, outcomes.get(file)));
            Map<String, Set<String>> guessed = new LinkedHashMap<>();
            guessedFields.forEach((file, fields) -> {
                if (outcomes.get(file).converted() != null) {
                    guessed.put(file, fields);
                }
            });
            return new GatsbyComponents(sorted, rootLayout, around.get(0), around.get(1), guessed);
        }

        private Outcome outcome(String file) {
            Outcome done = outcomes.get(file);
            if (done != null) {
                return done;
            }
            ReactComponent component = components.get(file);
            boolean page = file.startsWith(project.path("src/pages/"));
            Outcome outcome;
            if (page) {
                outcome = new Outcome(component, true, null, null, null);
            } else if (!converting.add(file)) {
                return null; // Components that include each other
            } else {
                Outcome result;
                try {
                    JsxToQuteConverter.Converted converted = JsxToQuteConverter.convert(component, this);
                    String template = file.equals(rootLayout) ? MAIN_LAYOUT : "partials/" + baseName(file);
                    result = new Outcome(component, false, template, converted, null);
                } catch (JsxToQuteConverter.Unsupported e) {
                    result = new Outcome(component, false, null, null, e.getMessage());
                }
                outcome = result;
            }
            outcomes.put(file, outcome);
            return outcome;
        }

        @Override
        public String templateFor(ReactComponent component, ReactComponent.Import imported) {
            String file = resolve(component.file(), imported.from());
            if (file == null) {
                return null;
            }
            Outcome outcome = outcome(file);
            return outcome == null ? null : outcome.template();
        }

        @Override
        public String imageUrl(ReactComponent component, String src) {
            String image = image(component, src);
            return image == null ? null
                    : "site.url('images/" + image.substring(project.imageSource().length() + 1) + "')";
        }

        @Override
        public ImageSize imageSize(ReactComponent component, String src) {
            String image = image(component, src);
            try {
                return image == null || !source.isFile(image) ? null : ImageSize.of(image, source.readBytes(image));
            } catch (MigrationException e) {
                return null;
            }
        }

        /**
         * @return the image file a StaticImage shows, in the site's image source; null if it is not one
         */
        private String image(ReactComponent component, String src) {
            if (project.imageSource() == null || src.contains("://")) {
                return null;
            }
            String image = GatsbyProject.normalise(parent(component.file()) + "/" + src);
            return image == null || !image.startsWith(project.imageSource() + "/") ? null : image;
        }

        @Override
        public JsxToQuteConverter.PageList pageList(String query) {
            return query.equals("allMarkdownRemark") ? MARKDOWN_PAGES : null;
        }

        @Override
        public String pageField(ReactComponent component, String page, String field) {
            if (field.equals(pagePath)) {
                // The Markdown route serves each page at this field, as Roq does at its slugified file name
                return page + ".baseFileName.slugify(true)";
            }
            Matcher frontMatter = FRONT_MATTER_FIELD.matcher(field);
            if (frontMatter.matches()) {
                return page + ".data." + frontMatter.group(1) + " ?: ''";
            }
            if (field.equals("fields.title")) {
                // Set by the site, usually from the front matter title, or the file name when there is none
                guessedFields.computeIfAbsent(component.file(), f -> new TreeSet<>()).add(field);
                return page + ".data.title ?: " + page + ".baseFileName.replace('-', ' ')";
            }
            return switch (field) {
                case "id" -> page + ".id";
                default -> null;
            };
        }

        @Override
        public boolean isPagePath(String field) {
            return field.equals(pagePath);
        }

        /**
         * @return the field the Markdown route serves pages at, e.g. {@code fields.slug}; null if there is no route
         */
        private String pagePath() {
            for (String file : components.keySet()) {
                Matcher route = MARKDOWN_ROUTE.matcher(file.substring(file.lastIndexOf('/') + 1));
                if (file.startsWith(project.path("src/pages/")) && route.matches()) {
                    return route.group(1).replace("__", ".");
                }
            }
            return null;
        }

        /**
         * @return the component that renders {children} and that most pages use
         */
        private String rootLayout() {
            String best = null;
            int bestUses = 0;
            for (ReactComponent component : components.values()) {
                if (component.jsx() == null || !component.jsx().contains("{children}")
                        || component.file().startsWith(project.path("src/pages/"))) {
                    continue;
                }
                int uses = 0;
                for (ReactComponent page : components.values()) {
                    if (page.file().startsWith(project.path("src/pages/")) && page.imports().values().stream()
                            .anyMatch(i -> i.isLocal() && component.file().equals(resolve(page.file(), i.from())))) {
                        uses++;
                    }
                }
                if (uses > bestUses) {
                    best = component.file();
                    bestUses = uses;
                }
            }
            return best;
        }

        /**
         * @return the component file an import names, e.g. {@code ./header} from {@code src/components/layout.js}
         *         is {@code src/components/header.js}; null if it is not one of the components
         */
        private String resolve(String from, String module) {
            String base = GatsbyProject.normalise(parent(from) + "/" + module);
            if (base == null) {
                return null;
            }
            List<String> candidates = new ArrayList<>(List.of(base));
            for (String extension : EXTENSIONS) {
                candidates.add(base + extension);
                candidates.add(base + "/index" + extension);
            }
            return candidates.stream().filter(components::containsKey).findFirst().orElse(null);
        }

        private static String parent(String file) {
            int slash = file.lastIndexOf('/');
            return slash < 0 ? "." : file.substring(0, slash);
        }

        private static String baseName(String file) {
            return file.substring(file.lastIndexOf('/') + 1).replaceAll("\\.[^.]+$", "");
        }
    }
}
