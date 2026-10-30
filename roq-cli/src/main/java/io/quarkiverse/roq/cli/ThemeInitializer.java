package io.quarkiverse.roq.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Scaffolds a theme developed alongside a Maven site: the theme sources live in a sub-directory which the site adds
 * to its resources (so dev mode hot-reloads them), and a separate pom packages them as a jar for other sites.
 */
public final class ThemeInitializer {

    public static final String DEFAULT_DIR = "theme";

    // Theme layout, relative to the theme resources
    static final Path TEMPLATES = Path.of("templates");
    static final Path WEB_APP = Path.of("web", "app");

    /**
     * The theme resources, relative to the site.
     */
    static Path resourcesDir(String themeDirName) {
        return Path.of(themeDirName, "src", "main", "resources");
    }

    static Path partialsDir(String themeName) {
        return TEMPLATES.resolve("partials").resolve(themeName);
    }

    static Path layoutsDir(String themeName) {
        return TEMPLATES.resolve("theme-layouts").resolve(themeName);
    }

    private static final Pattern THEME_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern ALT_EXPR_SYNTAX_ENABLED = Pattern.compile("alt-expr-syntax\\s*[:=]\\s*\"?true\"?");
    private static final List<String> CONFIG_FILES = List.of(
            "config/application.properties",
            "config/application.yml",
            "config/application.yaml",
            "src/main/resources/application.properties",
            "src/main/resources/application.yml",
            "src/main/resources/application.yaml");

    private ThemeInitializer() {
    }

    public record Result(Path themeDir, boolean altExprSyntax, boolean pomUpdated) {
    }

    /**
     * Create a preview site for a new theme in {@code siteDir}, then scaffold the theme in it with starter layouts,
     * enabled in both the jar and the preview site.
     */
    public static Result initWithPreviewSite(Path siteDir, String themeName, String themeDirName, String groupId,
            String roqVersion) throws Exception {
        validateThemeName(themeName);
        if (Files.exists(siteDir)) {
            throw new IllegalStateException("Directory '" + siteDir.getFileName() + "' already exists");
        }
        // The site artifact differs from the theme jar's
        boolean created = new RoqProjectCreator(siteDir, themeName + "-site")
                .groupId(groupId)
                .roqVersion(roqVersion)
                .extensions(List.of("theme:base"))
                .create();
        if (!created) {
            throw new IllegalStateException("Unable to create the preview site in " + siteDir);
        }
        Result result = init(siteDir, themeName, themeDirName, groupId, true);
        enableTheme(siteDir, themeName);
        return result;
    }

    public static Result init(Path siteDir, String themeName, String themeDirName, String groupId) throws IOException {
        return init(siteDir, themeName, themeDirName, groupId, false);
    }

    /**
     * @param starterLayouts whether to add starter layouts, for a new theme (an existing site moves its own layouts)
     */
    public static Result init(Path siteDir, String themeName, String themeDirName, String groupId, boolean starterLayouts)
            throws IOException {
        validateThemeName(themeName);
        Path pom = siteDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new IllegalStateException("No pom.xml found in " + siteDir + ": only Maven sites are supported");
        }
        Path themeDir = siteDir.resolve(themeDirName);
        if (Files.exists(themeDir)) {
            throw new IllegalStateException("Directory '" + themeDirName + "' already exists");
        }

        boolean altExprSyntax = isAltExprSyntaxEnabled(siteDir);
        Path resources = siteDir.resolve(resourcesDir(themeDirName));
        write(themeDir.resolve("pom.xml"), themePom(groupId, themeName));
        write(resources.resolve("META-INF/beans.xml"), "");
        if (starterLayouts) {
            write(resources.resolve("application.properties"),
                    "# Makes sites using this jar default to the theme layouts\n"
                            + "site.theme=" + themeName + "\n");
        } else {
            // Setting site.theme before the theme has layouts would break the sites using the jar
            write(resources.resolve("application.properties"),
                    "# Makes sites using this jar default to the theme layouts. Uncomment once the theme provides layouts.\n"
                            + "#site.theme=" + themeName + "\n");
        }
        if (altExprSyntax) {
            // The site's quarkus.qute.alt-expr-syntax doesn't apply to templates from a jar
            write(resources.resolve(TEMPLATES).resolve(".qute"), "alt-expr-syntax=true\n");
        }
        Files.createDirectories(resources.resolve(partialsDir(themeName)));
        Path layouts = resources.resolve(layoutsDir(themeName));
        Files.createDirectories(layouts);
        String styles = "// Styles of the " + themeName + " theme, included in the 'app' bundle of the sites using it\n";
        if (starterLayouts) {
            write(layouts.resolve("default.html"), starterTemplate(DEFAULT_LAYOUT, themeName, altExprSyntax));
            write(layouts.resolve("page.html"), starterTemplate(PAGE_LAYOUT, themeName, altExprSyntax));
            write(layouts.resolve("post.html"), starterTemplate(POST_LAYOUT, themeName, altExprSyntax));
            styles += starterTemplate(STYLES, themeName, altExprSyntax);
        }
        // Non-partial files in web/app/ are added to the site's bundle (and esbuild rejects unknown files there)
        write(resources.resolve(WEB_APP).resolve(themeName + ".scss"), styles);

        String pomContent = Files.readString(pom);
        String updated = addThemeResources(pomContent, resourcesDir(themeDirName));
        boolean pomUpdated = !updated.equals(pomContent);
        if (pomUpdated) {
            Files.writeString(pom, updated);
        }
        return new Result(themeDir, altExprSyntax, pomUpdated);
    }

    /**
     * Make the site use the theme layouts, in {@code config/application.properties}.
     */
    static void enableTheme(Path siteDir, String themeName) throws IOException {
        Path config = siteDir.resolve("config/application.properties");
        String content = Files.isRegularFile(config) ? Files.readString(config) : "";
        if (content.lines().anyMatch(l -> l.trim().startsWith("site.theme"))) {
            content = content.replaceAll("(?m)^\\s*site\\.theme\\s*=.*$", "site.theme=" + themeName);
        } else {
            content += (content.isEmpty() || content.endsWith("\n") ? "" : "\n") + "site.theme=" + themeName + "\n";
        }
        write(config, content);
    }

    private static void validateThemeName(String themeName) {
        if (!THEME_NAME.matcher(themeName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid theme name '" + themeName + "': use lowercase letters, digits and dashes (e.g. my-theme)");
        }
    }

    // Starter templates use the alternative expression syntax, converted when the site doesn't use it
    private static String starterTemplate(String template, String themeName, boolean altExprSyntax) {
        String content = template.replace("THEME", themeName);
        return altExprSyntax ? content : content.replace("{=", "{");
    }

    private static final String DEFAULT_LAYOUT = """
            ---
            layout: roq-base/default
            ---
            <header class="THEME-header">
              <a href="{=site.url}">{=site.title}</a>
            </header>
            {#insert /}
            <footer class="THEME-footer">
              <p>Made with the THEME theme</p>
            </footer>
            """;

    private static final String PAGE_LAYOUT = """
            ---
            layout: default
            ---
            <main class="THEME-main">
              <article>
                <h1>{=page.title}</h1>
                {#insert /}
              </article>
            </main>
            """;

    private static final String POST_LAYOUT = """
            ---
            layout: default
            ---
            <main class="THEME-main">
              <article>
                <h1>{=page.title}</h1>
                {#if page.date}<time>{=page.date.longDate}</time>{/if}
                {#insert /}
              </article>
            </main>
            """;

    private static final String STYLES = """
            .THEME-header, .THEME-footer {
              padding: 1rem 2rem;
            }

            .THEME-header a {
              font-weight: 600;
              color: inherit;
              text-decoration: none;
            }

            .THEME-main {
              max-width: 48rem;
              margin: 2rem auto;
              padding: 0 1rem;
            }
            """;

    static boolean isAltExprSyntaxEnabled(Path siteDir) throws IOException {
        for (String configFile : CONFIG_FILES) {
            Path path = siteDir.resolve(configFile);
            if (Files.isRegularFile(path) && ALT_EXPR_SYNTAX_ENABLED.matcher(Files.readString(path)).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the theme resource directory to the site pom, keeping out the files which only belong in the packaged jar.
     * Declaring {@code <resources>} replaces Maven's default, so {@code src/main/resources} is declared too when needed.
     */
    static String addThemeResources(String pom, Path themeResources) {
        // Maven paths use '/' on every OS
        String themeResourcesDir = StreamSupport.stream(themeResources.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
        if (pom.contains("<directory>" + themeResourcesDir + "</directory>")) {
            return pom;
        }
        String themeResource = """
                <resource>
                    <directory>%s</directory>
                    <excludes>
                        <!-- Only needed in the packaged theme jar -->
                        <exclude>META-INF/**</exclude>
                        <exclude>application.properties</exclude>
                    </excludes>
                </resource>
                """.formatted(themeResourcesDir);

        Matcher resourcesEnd = Pattern.compile("(?m)^([ \\t]*)</resources>").matcher(pom);
        if (resourcesEnd.find()) {
            String indent = resourcesEnd.group(1) + detectIndentUnit(pom);
            return pom.substring(0, resourcesEnd.start()) + indent(themeResource, indent) + pom.substring(resourcesEnd.start());
        }

        String unit = detectIndentUnit(pom);
        String resources = "<resources>\n"
                + indent("""
                        <resource>
                            <directory>src/main/resources</directory>
                        </resource>
                        """ + themeResource, unit)
                + "</resources>\n";

        Matcher buildStart = Pattern.compile("(?m)^([ \\t]*)<build>[ \\t]*\\n").matcher(pom);
        if (buildStart.find()) {
            return pom.substring(0, buildStart.end()) + indent(resources, buildStart.group(1) + unit)
                    + pom.substring(buildStart.end());
        }
        Matcher projectEnd = Pattern.compile("(?m)^([ \\t]*)</project>").matcher(pom);
        if (!projectEnd.find()) {
            throw new IllegalStateException("Unable to find </project> in pom.xml");
        }
        String build = "<build>\n" + indent(resources, unit) + "</build>\n";
        return pom.substring(0, projectEnd.start()) + indent(build, projectEnd.group(1) + unit)
                + pom.substring(projectEnd.start());
    }

    private static String detectIndentUnit(String pom) {
        Matcher m = Pattern.compile("(?m)^([ \\t]+)<").matcher(pom);
        return m.find() ? m.group(1) : "    ";
    }

    private static String indent(String text, String indent) {
        return text.lines().map(l -> l.isEmpty() ? l : indent + l).reduce("", (a, b) -> a + b + "\n");
    }

    private static String themePom(String groupId, String themeName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <!-- Packages the theme for other sites. The site in the parent directory uses these sources directly. -->
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0-SNAPSHOT</version>

                    <properties>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                </project>
                """
                .formatted(groupId, themeName);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
