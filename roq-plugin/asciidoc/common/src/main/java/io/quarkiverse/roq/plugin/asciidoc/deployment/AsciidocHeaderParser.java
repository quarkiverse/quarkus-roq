package io.quarkiverse.roq.plugin.asciidoc.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.ESCAPE_KEY;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.TemplateContext;
import io.vertx.core.json.JsonObject;
import io.yupiik.asciidoc.model.Header;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.internal.Reader;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;
import io.yupiik.asciidoc.parser.resolver.RelativeContentResolver;

public class AsciidocHeaderParser {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(AsciidocHeaderParser.class);

    private static final ContentResolver EMPTY_CONTENT_RESOLVER = (ref, encoding) -> Optional.empty();

    public static RoqFrontMatterHeaderParserBuildItem createBuildItem(boolean escape, Predicate<TemplateContext> isApplicable) {
        return new RoqFrontMatterHeaderParserBuildItem(isApplicable, templateContext -> {
            Parser parser = new Parser();
            ContentResolver contentResolver = new PathContentResolver(templateContext.sourceFile().getParent());
            Header header = parser.parseHeader(new Reader(templateContext.content().lines().toList()),
                    new Parser.ParserContext(contentResolver));
            final JsonObject pageData = toPageData(header);
            if (!pageData.containsKey(ESCAPE_KEY)) {
                pageData.put(ESCAPE_KEY, escape);
            }
            return pageData;
        }, Function.identity(), 20);
    }

    public static JsonObject toPageData(Header header) {
        JsonObject pageData = new JsonObject();
        for (String key : header.attributes().keySet()) {
            if (key.startsWith("page-")) {
                pageData.put(key.substring(5), header.attributes().get(key));
            }
        }
        if (!header.title().isBlank()) {
            pageData.put("title", header.title());
        }
        if (!header.author().name().isBlank()) {
            pageData.put("author", new JsonObject().put("name", header.author().name()).put("email", header.author().mail()));
        }
        if (!header.revision().number().isBlank()) {
            pageData.put("revision", new JsonObject().put("number", header.revision().number())
                    .put("date", header.revision().date()).put("remark", header.revision().revmark()));
        }
        return pageData;
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
