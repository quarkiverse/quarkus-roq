package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the user has to do about each Gatsby plugin a site uses.
 */
final class GatsbyPlugins {

    /**
     * Plugins whose job the migration already does.
     */
    static final List<String> HANDLED = List.of("gatsby-source-filesystem", "gatsby-transformer-remark");

    private static final String SHARP = "Roq does not resize or convert images (gatsby-plugin-sharp, "
            + "gatsby-transformer-sharp); resize the images in public/images/ if they are large";

    /**
     * Plugins with a known Roq counterpart, or none.
     */
    static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("gatsby-plugin-image", "Replace <StaticImage> and <GatsbyImage> with plain <img> tags; "
                    + "the images were copied to public/images/, and Roq does not resize them"),
            Map.entry("gatsby-plugin-sharp", SHARP),
            Map.entry("gatsby-transformer-sharp", SHARP),
            Map.entry("gatsby-plugin-manifest", "Add a web app manifest and icons to public/ if the site needs "
                    + "them (gatsby-plugin-manifest)"),
            Map.entry("gatsby-remark-autolink-headers", "Check that headings get anchor links as they did with "
                    + "gatsby-remark-autolink-headers"),
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
    static Set<String> actionItems(GatsbyProject project, Path siteDir, GatsbyComponents components) {
        Set<String> actions = new LinkedHashSet<>();
        for (String plugin : project.config().plugins()) {
            if (HANDLED.contains(plugin)) {
                continue;
            }
            if (plugin.equals("gatsby-plugin-image") && !unconvertedComponentsUse(components, plugin)) {
                // The converted components' images are plain <img> tags already
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

    private static boolean unconvertedComponentsUse(GatsbyComponents components, String module) {
        return components.outcomes().values().stream().filter(o -> !o.isConverted())
                .anyMatch(o -> o.component().imports().values().stream().anyMatch(i -> i.from().equals(module)));
    }
}
