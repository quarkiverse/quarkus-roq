package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.FrontMatter;
import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Does what {@code gatsby-remark-emoji} did, which Roq does not: turns emoji shortcodes such as {@code :warning:} in
 * the pages into emoji. Only the common ones are known; the others are reported. Code is left as it is.
 * <p>
 * TODO Once quarkus-qute-web-markdown renders emoji shortcodes (https://github.com/quarkiverse/quarkus-qute-web/issues/241),
 * this can go, and the pages keep their shortcodes.
 */
final class GatsbyEmoji {

    static final String PLUGIN = "gatsby-remark-emoji";
    /**
     * An emoji shortcode such as {@code :warning:}, not part of a longer run of letters and colons such as a time.
     */
    static final Pattern SHORTCODE = Pattern.compile("(?<![\\w:]):([a-z0-9_+-]+):(?![\\w:])");
    /**
     * Fenced code blocks and inline code, where a shortcode is not one.
     */
    static final Pattern CODE = Pattern.compile("(?ms)^(```|~~~).*?^\\1|`[^`\\n]*`");
    /**
     * The shortcodes pages most often have, with GitHub's emoji for them.
     */
    static final Map<String, String> EMOJI = Map.ofEntries(
            Map.entry("warning", "⚠️"),
            Map.entry("exclamation", "❗"),
            Map.entry("heavy_exclamation_mark", "❗"),
            Map.entry("question", "❓"),
            Map.entry("bulb", "💡"),
            Map.entry("memo", "📝"),
            Map.entry("pencil", "📝"),
            Map.entry("information_source", "ℹ️"),
            Map.entry("white_check_mark", "✅"),
            Map.entry("heavy_check_mark", "✔️"),
            Map.entry("x", "❌"),
            Map.entry("no_entry", "⛔"),
            Map.entry("no_entry_sign", "🚫"),
            Map.entry("construction", "🚧"),
            Map.entry("rotating_light", "🚨"),
            Map.entry("stop_sign", "🛑"),
            Map.entry("rocket", "🚀"),
            Map.entry("tada", "🎉"),
            Map.entry("sparkles", "✨"),
            Map.entry("fire", "🔥"),
            Map.entry("star", "⭐"),
            Map.entry("zap", "⚡"),
            Map.entry("boom", "💥"),
            Map.entry("100", "💯"),
            Map.entry("+1", "👍"),
            Map.entry("thumbsup", "👍"),
            Map.entry("-1", "👎"),
            Map.entry("thumbsdown", "👎"),
            Map.entry("clap", "👏"),
            Map.entry("wave", "👋"),
            Map.entry("pray", "🙏"),
            Map.entry("muscle", "💪"),
            Map.entry("ok_hand", "👌"),
            Map.entry("point_right", "👉"),
            Map.entry("eyes", "👀"),
            Map.entry("smile", "😄"),
            Map.entry("slightly_smiling_face", "🙂"),
            Map.entry("wink", "😉"),
            Map.entry("heart", "❤️"),
            Map.entry("bug", "🐛"),
            Map.entry("wrench", "🔧"),
            Map.entry("hammer", "🔨"),
            Map.entry("gear", "⚙️"),
            Map.entry("package", "📦"),
            Map.entry("lock", "🔒"),
            Map.entry("key", "🔑"),
            Map.entry("link", "🔗"),
            Map.entry("pushpin", "📌"),
            Map.entry("books", "📚"),
            Map.entry("book", "📖"),
            Map.entry("mag", "🔍"),
            Map.entry("calendar", "📆"),
            Map.entry("checkered_flag", "🏁"),
            Map.entry("arrow_right", "➡️"),
            Map.entry("recycle", "♻️"),
            Map.entry("coffee", "☕"));

    private GatsbyEmoji() {
    }

    /**
     * Turns the known shortcodes in the pages in {@code content/} into emoji, and reports the others.
     */
    static void convert(TargetSite site) throws IOException {
        Path content = site.dir().resolve("content");
        List<String> converted = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (Path page : SiteFiles.files(content, p -> GatsbyProject.MARKDOWN.matcher(p.toString()).matches())) {
            String text = Files.readString(page);
            String frontMatter = FrontMatter.block(text);
            Set<String> pageConverted = new LinkedHashSet<>();
            Set<String> pageUnknown = new LinkedHashSet<>();
            String body = outsideCode(text.substring(frontMatter.length()), prose -> {
                Matcher shortcode = SHORTCODE.matcher(prose);
                StringBuilder out = new StringBuilder();
                while (shortcode.find()) {
                    String emoji = EMOJI.get(shortcode.group(1));
                    (emoji == null ? pageUnknown : pageConverted).add(shortcode.group());
                    shortcode.appendReplacement(out, Matcher.quoteReplacement(emoji == null ? shortcode.group() : emoji));
                }
                return shortcode.appendTail(out).toString();
            });
            String where = " in " + SiteFiles.relativePath(site.dir(), page);
            if (!pageConverted.isEmpty()) {
                Files.writeString(page, frontMatter + body);
                converted.add(String.join(" ", pageConverted) + where);
            }
            if (!pageUnknown.isEmpty()) {
                unknown.add(String.join(" ", pageUnknown) + where);
            }
        }
        if (!converted.isEmpty()) {
            site.report().info("Turned emoji shortcodes into emoji, as " + PLUGIN + " did and Roq does not: "
                    + String.join(", ", converted));
        }
        if (!unknown.isEmpty()) {
            site.report().todo("Replace these emoji shortcodes with the emoji, which " + PLUGIN + " turned them into "
                    + "and Roq does not; the migration only knows the common ones: " + String.join(", ", unknown));
        }
    }

    /**
     * @return the text with what is outside code changed, and the code as it is
     */
    static String outsideCode(String text, UnaryOperator<String> change) {
        Matcher code = CODE.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (code.find()) {
            out.append(change.apply(text.substring(last, code.start()))).append(code.group());
            last = code.end();
        }
        return out.append(change.apply(text.substring(last))).toString();
    }
}
