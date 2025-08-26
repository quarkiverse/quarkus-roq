package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.ESCAPE_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.stripFrontMatter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.TemplateContext;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.vertx.core.json.JsonObject;
import io.yupiik.asciidoc.model.Header;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.internal.Reader;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;
import io.yupiik.asciidoc.parser.resolver.RelativeContentResolver;

public class AsciidocHeaderParser {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(AsciidocHeaderParser.class);

    private static final ContentResolver EMPTY_CONTENT_RESOLVER = (ref, encoding) -> Optional.empty();
    private static final String QUTE_KEY = "qute";

    public static RoqFrontMatterHeaderParserBuildItem createBuildItem(boolean qute, Predicate<TemplateContext> isApplicable) {
        return new RoqFrontMatterHeaderParserBuildItem(isApplicable, templateContext -> {
            Parser parser = new Parser();
            String content = stripFrontMatter(templateContext.content());
            ContentResolver contentResolver = new PathContentResolver(templateContext.sourceFile().getParent());
            Header header = parser.parseHeader(new Reader(content.lines().toList()),
                    new Parser.ParserContext(contentResolver));
            final JsonObject pageData = toPageData(header);
            boolean escape = !qute;

            if (header.attributes().containsKey(QUTE_KEY)) {
                final String quteAttribute = header.attributes().get(QUTE_KEY);
                escape = !(quteAttribute.isEmpty() || Boolean.parseBoolean(quteAttribute));
            }

            if (!pageData.containsKey(ESCAPE_KEY)) {
                pageData.put(ESCAPE_KEY, escape);
            }
            return pageData;
        }, Function.identity(), 20);
    }

    public static JsonObject toPageData(Header header) {
        JsonObject pageData = new JsonObject();
        final Map<String, String> attributes = processAttributes(header.attributes());
        pageData.put("attributes", JsonObject.mapFrom(attributes));
        for (String key : attributes.keySet()) {
            if (key.startsWith("page-")) {
                final String value = attributes.get(key);
                pageData.put(key.substring(5), value.isEmpty() ? true : value);
            }
        }
        if (!header.title().isBlank()) {
            pageData.put(Page.FM_TITLE, header.title());
        }
        if (header.attributes().containsKey(Page.FM_DESCRIPTION) && !header.attributes().get(Page.FM_DESCRIPTION).isBlank()) {
            pageData.put(Page.FM_DESCRIPTION, header.attributes().get("description"));
        }
        if (!header.author().name().isBlank()) {
            pageData.put("author", header.author().name());
            pageData.put("author-email", header.author().mail());
        }
        if (!header.revision().number().isBlank()) {
            pageData.put("revision", new JsonObject()
                    .put("number", header.revision().number())
                    .put("date", header.revision().date())
                    .put("remark", header.revision().revmark()));
        }
        if (attributes.containsKey("description")) {
            pageData.put(Page.FM_DESCRIPTION, attributes.get("description"));
        }
        if (attributes.containsKey("image")) {
            pageData.put("image", attributes.get("image"));
        }
        return pageData;
    }

    public static Map<String, String> processAttributes(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith("!")) {
                result.remove(key.substring(1));
            } else {
                result.put(key, entry.getValue());
            }
        }

        return result;
    }

    static class PathContentResolver implements RelativeContentResolver {

        private final Path base;

        PathContentResolver(Path base) {
            this.base = base;
        }

        @Override
        public Optional<Resolved> resolve(Path parent, String ref, Charset encoding) {
            final var rel = Path.of(ref);
            if (rel.isAbsolute()) {
                return doRead(rel, encoding);
            }
            if (parent != null && parent.getParent() != null) {
                return doRead(parent.getParent().resolve(ref), encoding);
            }
            final var resolved = base.resolve(ref);
            return doRead(resolved, encoding);
        }

        private static Optional<Resolved> doRead(Path resolved, Charset encoding) {
            if (Files.isRegularFile(resolved)) {
                try {
                    return Optional.of(new Resolved(resolved, Files.readAllLines(resolved, encoding)));
                } catch (IOException e) {
                    throw new RuntimeException("Can't read '" + resolved + "'");
                }
            }
            LOGGER.warnf("Include file '%s' not found", resolved);
            return Optional.of(new Resolved(null, List.of()));
        }
    }

}
