package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
 */
record GatsbyProject(String root, GatsbyConfig config, List<String> markdownSources, String imageSource,
        String homePage) {

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
        return new GatsbyProject(root, config, markdownSources, imageSource, homePage);
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
