package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.roq.migration.SiteFiles;

/**
 * What the user has to do about each Gatsby plugin a site uses.
 */
final class GatsbyPlugins {

    /**
     * Plugins whose job the migration already does.
     */
    static final List<String> HANDLED = List.of("gatsby-source-filesystem", "gatsby-transformer-remark");

    /**
     * The plugins that resized and converted the site's images, which Roq does not.
     */
    static final Set<String> SHARP = Set.of("gatsby-plugin-sharp", "gatsby-transformer-sharp");
    /**
     * The size above which an image is large enough to need resizing, in bytes.
     */
    static final long LARGE_IMAGE = 500 * 1024;

    /**
     * Plugins whose job Roq does in part, and what is left out, which nothing has to be done about.
     */
    static final Map<String, String> NOTES = Map.of(
            "gatsby-remark-autolink-headers", "Headings keep the ids that gatsby-remark-autolink-headers gave them, "
                    + "in the same GitHub style, so links to them still work; the link icon it showed on hover is not "
                    + "migrated");

    /**
     * Plugins with a known Roq counterpart, or none.
     */
    static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("gatsby-plugin-image", "Replace <StaticImage> and <GatsbyImage> with plain <img> tags; "
                    + "the images were copied to public/images/, and Roq does not resize them"),
            Map.entry("gatsby-plugin-manifest", "Add a web app manifest and icons to public/ if the site needs "
                    + "them (gatsby-plugin-manifest)"),
            Map.entry("gatsby-remark-emoji", "Emoji shortcodes such as :smile: are not converted any more "
                    + "(gatsby-remark-emoji); replace them with the characters"),
            Map.entry("gatsby-remark-prismjs", "Add syntax highlighting to code blocks (gatsby-remark-prismjs), "
                    + "for example with highlight.js in web/main.js"));

    private GatsbyPlugins() {
    }

    /**
     * @param siteDir the site being converted, where local plugins live in the Gatsby project's plugins/
     * @param components the site's React components, to leave out what their conversion already did
     * @return one action item per thing to do, in the order the plugins are configured
     */
    static Set<String> actionItems(GatsbyProject project, Path siteDir, GatsbyComponents components)
            throws IOException {
        Set<String> actions = new LinkedHashSet<>();
        for (String plugin : project.config().plugins()) {
            if (HANDLED.contains(plugin) || NOTES.containsKey(plugin)) {
                continue;
            }
            if (plugin.equals("gatsby-plugin-image") && !unconvertedComponentsUse(components, plugin)) {
                // The converted components' images are plain <img> tags already
                continue;
            }
            if (SHARP.contains(plugin)) {
                // Only for the images large enough to need it
                String images = largeImages(siteDir);
                if (images != null) {
                    actions.add("Resize the large images in public/, which " + String.join(" and ", SHARP.stream()
                            .filter(project.config().plugins()::contains).sorted().toList()) + " resized and Roq does "
                            + "not: " + images);
                }
                continue;
            }
            String local = project.path("plugins/" + plugin);
            if (ACTIONS.containsKey(plugin)) {
                actions.add(ACTIONS.get(plugin));
            } else if (Files.isDirectory(siteDir.resolve(local))) {
                actions.add("Reproduce the local Gatsby plugin " + local + "/ (see its gatsby-node.js); it has no Roq "
                        + "equivalent");
            } else {
                actions.add("Find a Roq replacement for the Gatsby plugin " + plugin + ", or drop it");
            }
        }
        return actions;
    }

    /**
     * @return the images in {@code public/} over {@link #LARGE_IMAGE}, with their sizes, e.g.
     *         {@code images/photo.jpg (1.2 MB)}; null if there are none
     */
    static String largeImages(Path siteDir) throws IOException {
        Path site = siteDir.resolve("public");
        List<String> large = new ArrayList<>();
        for (Path image : SiteFiles.files(site, p -> GatsbyProject.IMAGE.matcher(p.toString()).matches())) {
            long size = Files.size(image);
            if (size > LARGE_IMAGE) {
                large.add(SiteFiles.relativePath(site, image)
                        + String.format(Locale.ROOT, " (%.1f MB)", size / (1024.0 * 1024)));
            }
        }
        return large.isEmpty() ? null : String.join(", ", large);
    }

    private static boolean unconvertedComponentsUse(GatsbyComponents components, String module) {
        return components.outcomes().values().stream().filter(o -> o.converted() == null)
                .anyMatch(o -> o.component().imports().values().stream().anyMatch(i -> i.from().equals(module)));
    }

    /**
     * @return what the site's plugins do that Roq does in part, in the order the plugins are configured
     */
    static List<String> notes(GatsbyProject project) {
        return project.config().plugins().stream().filter(NOTES::containsKey).map(NOTES::get).toList();
    }
}
