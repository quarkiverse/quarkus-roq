package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.quarkiverse.roq.migration.FrontMatter;
import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Converts Gatsby front matter to Roq's, and gives the site its index page.
 * <p>
 * Pages without front matter are left alone, so whatever else uses them sees the same files; the layouts give them a
 * title from their file name, and {@code site.page-link=/:path/} a Gatsby-style URL.
 */
final class GatsbyFrontMatter {

    private GatsbyFrontMatter() {
    }

    /**
     * @param homePage the home page relative to {@code content/}, or null
     */
    static void convert(GatsbyProject project, TargetSite site, String homePage, GatsbyComponents components)
            throws Exception {
        Path content = site.dir().resolve("content");
        Map<String, String> metadata = project.config().siteMetadata();
        int converted = 0;
        for (Path file : SiteFiles.files(content, p -> GatsbyProject.MARKDOWN.matcher(p.toString()).matches())) {
            String name = content.relativize(file).toString().replace('\\', '/');
            String text = Files.readString(file);
            String frontMatter = FrontMatter.block(text);
            String body = text.substring(frontMatter.length());
            if (name.equals("index.md")) {
                Files.writeString(file, "---\n" + withIndexMetadata(yaml(frontMatter), metadata) + "---\n" + body);
            } else if (!frontMatter.isEmpty()) {
                // Gatsby sites route pages by a slug or path field; Roq calls it link
                String yaml = yaml(frontMatter);
                String linked = yaml.replaceAll("(?m)^(?:slug|path):[ \\t]*[\"']?/?([^\"'\\s]*?)/?[\"']?[ \\t]*$",
                        "link: /$1/");
                if (!linked.equals(yaml)) {
                    Files.writeString(file, "---\n" + linked + "---\n" + body);
                    converted++;
                }
            }
        }
        if (converted > 0) {
            site.report().info("Turned the slug or path of " + converted + " pages into a link");
        }
        if (homePage != null && !homePage.equals("index.md") && components.indexPage() == null) {
            // The home page keeps its name; an index page shows it at /
            Files.writeString(content.resolve("index.html"), "---\n" + withIndexMetadata("", metadata) + "---\n"
                    + "{=site.pageContent(site.page('" + homePage + "')).raw}\n");
            site.report().info("Wrote content/index.html, which shows " + homePage + " at /");
        }
        site.report().todo("Check the page URLs against the old site; pages without a slug or path are served at "
                + "/<file-name>/, lower case with punctuation replaced by - (site.page-link=/:path/)");
    }

    /**
     * @return the index page's front matter, with the site's title and description from {@code siteMetadata}
     */
    static String withIndexMetadata(String yaml, Map<String, String> metadata) {
        StringBuilder index = new StringBuilder();
        if (!has(yaml, "title") && metadata.containsKey("title")) {
            index.append("title: ").append(quote(metadata.get("title"))).append("\n");
        }
        if (!has(yaml, "description") && metadata.containsKey("description")) {
            index.append("description: ").append(quote(metadata.get("description"))).append("\n");
        }
        if (!has(yaml, "layout")) {
            // The home page shows its content without repeating the site title as a heading
            index.append("layout: main\n");
        }
        if (!has(yaml, "link")) {
            // site.page-link=/:path/ would put it at /index/
            index.append("link: /\n");
        }
        return index + yaml;
    }

    private static boolean has(String yaml, String key) {
        return yaml.matches("(?s)(.*\\n)?" + key + ":.*");
    }

    private static String yaml(String frontMatter) {
        return frontMatter.isEmpty() ? "" : frontMatter.replaceAll("(?s)^---\\R(.*?)---\\R?$", "$1");
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
