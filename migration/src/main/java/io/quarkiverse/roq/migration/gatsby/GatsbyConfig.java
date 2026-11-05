package io.quarkiverse.roq.migration.gatsby;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the migration needs from a {@code gatsby-config.js}. The file is JavaScript, so it is read with patterns that
 * cover the usual shape of a config ({@code module.exports = { siteMetadata: {...}, plugins: [...] }}) rather than
 * evaluated; anything else is left for the user.
 *
 * @param siteMetadata the string values of {@code siteMetadata}, e.g. {@code title} and {@code siteUrl}
 * @param plugins the names of the plugins used, including nested ones such as remark plugins
 * @param sources the {@code gatsby-source-filesystem} sources, with paths relative to the Gatsby project
 * @param pluginOptions the string values of the options of the plugins configured with
 *        {@code resolve: ..., options: {...}}, by plugin; the first configuration of a plugin used more than once
 */
public record GatsbyConfig(Map<String, String> siteMetadata, Set<String> plugins, List<Source> sources,
        Map<String, Map<String, String>> pluginOptions) {

    public record Source(String name, String path) {
    }

    private static final String QUOTE = "[`'\"]";
    // The body of an object literal without nested objects; JavaScript template expressions such as ${__dirname} allowed
    private static final String OBJECT_BODY = "\\{((?:[^{}]|\\$\\{[^}]*})*)}";
    private static final Pattern SITE_METADATA = Pattern.compile("siteMetadata\\s*:\\s*" + OBJECT_BODY);
    private static final Pattern STRING_ENTRY = Pattern.compile("(\\w+)\\s*:\\s*" + QUOTE + "([^`'\"]*)" + QUOTE);
    // A plugin is named by resolve: "...", or by a string on its own in a plugins list
    private static final Pattern PLUGIN_NAME = Pattern.compile(
            "(?m)(?:resolve\\s*:\\s*|^\\s*)" + QUOTE + "(gatsby-[\\w-]+)" + QUOTE + "\\s*(?:,|$)");
    private static final Pattern FILESYSTEM_SOURCE = Pattern.compile(
            "resolve\\s*:\\s*" + QUOTE + "gatsby-source-filesystem" + QUOTE + "\\s*,\\s*options\\s*:\\s*" + OBJECT_BODY);
    private static final Pattern PLUGIN_OPTIONS = Pattern.compile(
            "resolve\\s*:\\s*" + QUOTE + "(gatsby-[\\w-]+)" + QUOTE + "\\s*,\\s*options\\s*:\\s*" + OBJECT_BODY);
    private static final Pattern DIRNAME_PREFIX = Pattern.compile("^\\$\\{__dirname}/|^\\./");

    public static GatsbyConfig parse(String javascript) {
        String js = stripComments(javascript);

        Map<String, String> siteMetadata = new LinkedHashMap<>();
        Matcher metadata = SITE_METADATA.matcher(js);
        if (metadata.find()) {
            Matcher entry = STRING_ENTRY.matcher(metadata.group(1));
            while (entry.find()) {
                siteMetadata.put(entry.group(1), entry.group(2));
            }
        }

        Set<String> plugins = new LinkedHashSet<>();
        Matcher plugin = PLUGIN_NAME.matcher(js);
        while (plugin.find()) {
            plugins.add(plugin.group(1));
        }

        List<Source> sources = new ArrayList<>();
        Matcher source = FILESYSTEM_SOURCE.matcher(js);
        while (source.find()) {
            Map<String, String> options = new LinkedHashMap<>();
            Matcher entry = STRING_ENTRY.matcher(source.group(1));
            while (entry.find()) {
                options.put(entry.group(1), entry.group(2));
            }
            String path = options.get("path");
            if (path != null) {
                sources.add(new Source(options.getOrDefault("name", ""), DIRNAME_PREFIX.matcher(path).replaceFirst("")));
            }
        }
        Map<String, Map<String, String>> pluginOptions = new LinkedHashMap<>();
        Matcher configured = PLUGIN_OPTIONS.matcher(js);
        while (configured.find()) {
            if (!pluginOptions.containsKey(configured.group(1))) {
                Map<String, String> options = new LinkedHashMap<>();
                Matcher entry = STRING_ENTRY.matcher(configured.group(2));
                while (entry.find()) {
                    options.put(entry.group(1), entry.group(2));
                }
                pluginOptions.put(configured.group(1), options);
            }
        }
        return new GatsbyConfig(siteMetadata, plugins, sources, pluginOptions);
    }

    private static String stripComments(String js) {
        // Block comments, then line comments that are not part of a URL such as https://
        return js.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)(^|[^:])//.*$", "$1");
    }
}
