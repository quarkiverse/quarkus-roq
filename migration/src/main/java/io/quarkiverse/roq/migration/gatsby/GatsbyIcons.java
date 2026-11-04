package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import io.quarkiverse.roq.migration.engine.MigrationReport;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Migrates the favicon and web app manifest that {@code gatsby-plugin-manifest} made from one icon.
 * <p>
 * The icon goes in {@code public/images/} under the names Roq's {@code {#favicon}} tag discovers, at the size it is,
 * since Roq does not resize images: browsers scale a favicon themselves. The manifest is written from the plugin's
 * options, with that one icon.
 */
final class GatsbyIcons {

    static final String PLUGIN = "gatsby-plugin-manifest";
    static final String MANIFEST = "manifest.webmanifest";
    static final Set<String> FAVICON_TYPES = Set.of("png", "svg", "ico");
    /**
     * The names Gatsby's starters give the manifest, which sites often leave as they are.
     */
    static final Set<String> STARTER_NAMES = Set.of("gatsby-starter-default", "starter", "GatsbyJS", "Gatsby");
    /**
     * The manifest options the plugin passes on to the manifest as they are.
     */
    static final List<String> MANIFEST_OPTIONS = List.of("name", "short_name", "description", "start_url",
            "background_color", "theme_color", "display", "lang");

    private GatsbyIcons() {
    }

    /**
     * @return true if the site's manifest plugin has an icon this migrates
     */
    static boolean handles(GatsbyProject project) {
        Map<String, String> options = project.config().pluginOptions().get(PLUGIN);
        return options != null && options.containsKey("icon") && FAVICON_TYPES.contains(extension(options.get("icon")));
    }

    /**
     * Copies the icon and writes the manifest.
     *
     * @return the tags the layout's head needs for them
     */
    static List<String> convert(GatsbyProject project, TargetSite site) throws IOException {
        if (!handles(project)) {
            return List.of();
        }
        MigrationReport report = site.report();
        Map<String, String> options = project.config().pluginOptions().get(PLUGIN);
        Path icon = site.dir().resolve(project.path(options.get("icon")));
        if (!Files.isRegularFile(icon)) {
            report.todo("Add a favicon to public/images/: the icon " + options.get("icon") + " that " + PLUGIN
                    + " made the favicon from was not found");
            return List.of();
        }
        String extension = extension(icon.toString());
        Path images = Files.createDirectories(site.dir().resolve("public/images"));
        String favicon = "favicon." + extension;
        List<String> written = new ArrayList<>();
        if (copyIfAbsent(icon, images.resolve(favicon), report)) {
            written.add("public/images/" + favicon);
        }
        if (extension.equals("png") && copyIfAbsent(icon, images.resolve("apple-touch-icon.png"), report)) {
            written.add("public/images/apple-touch-icon.png");
        }
        if (!written.isEmpty()) {
            report.info("Copied the icon " + options.get("icon") + " that " + PLUGIN + " made the favicons from to "
                    + String.join(" and ", written) + ", which {#favicon} links; Roq does not resize them");
        }
        List<String> head = new ArrayList<>(List.of("{#favicon site /}"));

        Path manifest = site.dir().resolve("public").resolve(MANIFEST);
        if (Files.exists(manifest)) {
            report.info("Kept public/" + MANIFEST + ", which the site has already");
        } else {
            Files.writeString(manifest, manifest(options, favicon, sizes(icon)));
            report.info("Wrote public/" + MANIFEST + " from the options of " + PLUGIN);
        }
        head.add("<link rel=\"manifest\" href=\"{=site.url('" + MANIFEST + "')}\">");
        String name = options.getOrDefault("name", "");
        if (STARTER_NAMES.contains(name) || STARTER_NAMES.contains(options.getOrDefault("short_name", ""))) {
            report.todo("Give public/" + MANIFEST + " the site's name: it has the Gatsby starter's (" + name + ", "
                    + options.getOrDefault("short_name", "") + "), as the site's " + PLUGIN + " options did; or "
                    + "delete it and its link in templates/layouts/main.html if the site is not meant to be installed "
                    + "as an app");
        }
        return head;
    }

    /**
     * @param sizes the icon's size, e.g. {@code 200x200}, or {@code any}
     * @return the web app manifest
     */
    static String manifest(Map<String, String> options, String favicon, String sizes) {
        StringBuilder json = new StringBuilder("{\n");
        for (String option : MANIFEST_OPTIONS) {
            if (options.containsKey(option)) {
                json.append("  \"").append(option).append("\": \"").append(escape(options.get(option))).append("\",\n");
            }
        }
        String type = switch (extension(favicon)) {
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            default -> "image/png";
        };
        json.append("  \"icons\": [\n    { \"src\": \"images/").append(favicon).append("\", \"sizes\": \"").append(sizes)
                .append("\", \"type\": \"").append(type).append("\" }\n  ]\n}\n");
        return json.toString();
    }

    /**
     * @return the icon's size as a manifest gives it, e.g. {@code 200x200}, read from its header; {@code any} for an
     *         SVG, which scales, or an image the JDK cannot read, such as an ICO
     */
    static String sizes(Path icon) {
        if (extension(icon.toString()).equals("svg")) {
            return "any";
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(icon.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers != null && readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return reader.getWidth(0) + "x" + reader.getHeight(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            // Its size is not known
        }
        return "any";
    }

    private static boolean copyIfAbsent(Path from, Path to, MigrationReport report) throws IOException {
        if (Files.exists(to)) {
            report.info("Kept public/images/" + to.getFileName() + ", which the site has already");
            return false;
        }
        Files.copy(from, to);
        return true;
    }

    private static String extension(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
