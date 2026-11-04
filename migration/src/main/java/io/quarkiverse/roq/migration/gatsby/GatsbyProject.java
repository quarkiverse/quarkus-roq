package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.MvnpmDependencies;
import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.SourceSite;

/**
 * Where the parts of a Gatsby site are, read from the source site during the analyze stage. All paths are relative to
 * the migrated directory.
 *
 * @param root the directory of the Gatsby project: {@code ""} for the migrated directory, or e.g. {@code site}
 * @param config what {@code gatsby-config.js} says
 * @param markdownSources the {@code gatsby-source-filesystem} directories holding Markdown pages
 * @param imageSource the {@code gatsby-source-filesystem} directory holding images, or null
 * @param homePage the Markdown page shown at {@code /}, e.g. {@code docs/Home.md}, or null
 * @param browserImports what {@code gatsby-browser.js} imports, in order; its global styles
 * @param npmVersions the installed version of each npm package {@code gatsby-browser.js} imports for its side effects,
 *        such as a font; null where neither package-lock.json nor package.json says
 */
record GatsbyProject(String root, GatsbyConfig config, List<String> markdownSources, String imageSource,
        String homePage, List<BrowserImport> browserImports, Map<String, String> npmVersions) {

    /**
     * @param bindsNames true for {@code import x from "y"}, used by code; false for {@code import "y"}, such as a
     *        stylesheet or font
     */
    record BrowserImport(String module, boolean bindsNames) {

        boolean isLocal() {
            return module.startsWith(".");
        }
    }

    static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:([^\"';]+?)\\s+from\\s+)?[\"']([^\"']+)[\"']");

    static final List<String> CONFIG_FILES = List.of("gatsby-config.js", "gatsby-config.mjs", "gatsby-config.ts");
    static final List<String> PROJECT_DIRS = List.of("", "site", "website", "www");
    static final List<String> HOME_PAGES = List.of("index.md", "Home.md", "home.md", "README.md");
    static final Pattern MARKDOWN = Pattern.compile("(?i).*\\.(md|markdown)$");
    static final Pattern IMAGE = Pattern.compile("(?i).*\\.(png|jpe?g|gif|svg|webp)$");

    /**
     * @return the Gatsby project in the source site, or null if there is none
     */
    static String findRoot(SourceSite source) {
        for (String dir : PROJECT_DIRS) {
            for (String config : CONFIG_FILES) {
                if (source.isFile(join(dir, config))) {
                    return dir;
                }
            }
        }
        return null;
    }

    static GatsbyProject read(SourceSite source) throws MigrationException {
        String root = findRoot(source);
        String configFile = CONFIG_FILES.stream().map(f -> join(root, f)).filter(source::isFile).findFirst()
                .orElseThrow();
        GatsbyConfig config = GatsbyConfig.parse(source.readString(configFile));

        List<String> markdownSources = new ArrayList<>();
        String imageSource = null;
        for (GatsbyConfig.Source s : config.sources()) {
            String path = normalise(join(root, s.path()));
            if (path == null) {
                continue; // Outside the migrated directory; reported when restructuring
            }
            List<String> files = source.files(path);
            if (files.stream().anyMatch(f -> MARKDOWN.matcher(f).matches())) {
                markdownSources.add(path);
            } else if (imageSource == null && files.stream().anyMatch(f -> IMAGE.matcher(f).matches())) {
                imageSource = path;
            }
        }

        String homePage = null;
        for (String dir : markdownSources) {
            for (String candidate : HOME_PAGES) {
                if (homePage == null && source.isFile(dir + "/" + candidate)) {
                    homePage = dir + "/" + candidate;
                }
            }
        }

        List<BrowserImport> browserImports = new ArrayList<>();
        Map<String, String> npmVersions = new LinkedHashMap<>();
        String browser = join(root, "gatsby-browser.js");
        if (source.isFile(browser)) {
            String lock = source.isFile(join(root, "package-lock.json")) ? source.readString(join(root, "package-lock.json"))
                    : null;
            String packageJson = source.isFile(join(root, "package.json")) ? source.readString(join(root, "package.json"))
                    : null;
            Matcher imported = IMPORT.matcher(source.readString(browser));
            while (imported.find()) {
                BrowserImport browserImport = new BrowserImport(imported.group(2), imported.group(1) != null);
                browserImports.add(browserImport);
                if (!browserImport.isLocal() && !browserImport.bindsNames()) {
                    String npm = MvnpmDependencies.npmPackage(browserImport.module());
                    try {
                        npmVersions.putIfAbsent(npm, MvnpmDependencies.version(lock, packageJson, npm));
                    } catch (IOException e) {
                        throw new MigrationException("Cannot read the npm package versions: " + e.getMessage(), e);
                    }
                }
            }
        }
        return new GatsbyProject(root, config, markdownSources, imageSource, homePage, browserImports, npmVersions);
    }

    /**
     * @return a path in the Gatsby project, relative to the migrated directory
     */
    String path(String relative) {
        return join(root, relative);
    }

    /**
     * @return true for a React component or page, but not its tests
     */
    static boolean isReactSource(Path file) {
        String name = file.getFileName().toString();
        return name.matches(".*\\.(jsx?|tsx?)$") && !name.matches(".*\\.test\\.(jsx?|tsx?)$");
    }

    static String join(String dir, String path) {
        return dir.isEmpty() ? path : dir + "/" + path;
    }

    /**
     * @return the path with {@code ..} resolved, or null if it leaves the migrated directory
     */
    static String normalise(String path) {
        String normalised = Path.of(path).normalize().toString().replace('\\', '/');
        return normalised.startsWith("..") ? null : normalised;
    }
}
