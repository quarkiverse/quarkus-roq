package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConversion;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.plugin.asciidoc.common.runtime.RoqAsciidocKeys;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.vertx.core.json.JsonObject;
import io.yupiik.asciidoc.model.ConditionalBlock;
import io.yupiik.asciidoc.model.Document;
import io.yupiik.asciidoc.model.Header;
import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.resolver.RelativeContentResolver;
import io.yupiik.asciidoc.renderer.UnknownMacro;
import io.yupiik.asciidoc.renderer.VisitorSibling;
import io.yupiik.asciidoc.renderer.markdown.GithubFlavoredMarkdownRenderer;

/**
 * Converts one AsciiDoc source file to Markdown in-process: strip the Roq front matter, parse with the pure-Java
 * {@code asciidoc-java} parser (which resolves {@code include::} directives while parsing), then render with its
 * {@link GithubFlavoredMarkdownRenderer}. Contributed to the build as the {@link RoqMarkdownConverterBuildItem} of the
 * {@code asciidoc} markup, for the Markdown twin plugin and any other build step that needs a page as Markdown.
 * <p>
 * The page attributes mirror the ones the AsciidoctorJ plugin injects when it renders the page ({@code site-url},
 * {@code site-path}, {@code page-url}, {@code page-path}, {@code docname} and {@code relfileprefix}), so references
 * such as {@code {site-url}} and cross references resolve the same way in the Markdown; the site's
 * {@code quarkus.asciidoc.attributes.*} and the page's {@code asciidoc-attributes} front matter apply as on the page.
 * <p>
 * The conversion never fails a build: any problem, a {@link StackOverflowError} thrown by the parser included, is
 * logged at WARN and the caller gets an empty result, so the page simply has no Markdown.
 */
public final class AsciidocMarkdownConverter implements RoqMarkdownConverterBuildItem.MarkdownConverter {

    private static final Logger LOG = Logger.getLogger(AsciidocMarkdownConverter.class);

    /**
     * What a cross reference to {@code page.adoc} ends with unless the conversion says otherwise: {@code /index.md},
     * where the Markdown of each page is published by the Markdown twin plugin, where the HTML page ends with {@code /}
     * ({@code relfilesuffix} in the AsciidoctorJ plugin).
     */
    static final String DEFAULT_CROSS_REFERENCE_SUFFIX = "/index.md";

    private static final String TRUE = Boolean.TRUE.toString();

    /**
     * Prefix of a cross reference when the page URL is unknown; with a URL the processor passes its parent path, as the
     * AsciidoctorJ plugin does for the HTML page.
     */
    static final String DEFAULT_RELATIVE_PREFIX = "../";

    /** Which files an {@code include::} may read, mirroring the safe mode the AsciidoctorJ plugin renders the page with. */
    public enum Includes {
        /** None, as in {@code SECURE} mode. */
        NONE,
        /** Files inside the site directory, as in {@code SAFE} and {@code SERVER} mode. */
        SITE,
        /** Any file, as in {@code UNSAFE} mode. */
        ANY
    }

    private final Map<String, String> siteAttributes;
    private final Includes includes;

    /**
     * @param siteAttributes attributes the site configures ({@code quarkus.asciidoc.attributes.*}), which win over the
     *        attributes computed for a page, as they do on the rendered page; written as Asciidoctor API attributes,
     *        so {@code !name} unsets {@code name}
     * @param includes which files an {@code include::} may read
     */
    public AsciidocMarkdownConverter(Map<String, String> siteAttributes, Includes includes) {
        this.siteAttributes = siteAttributes == null ? Map.of() : siteAttributes;
        this.includes = includes == null ? Includes.SITE : includes;
    }

    /**
     * The site attributes as the AsciidoctorJ plugin reads them from {@code quarkus.asciidoc.attributes.*}:
     * {@code true} becomes an empty value, because SmallRye Config cannot hold an empty map value for an attribute that
     * is only set. An unset marker ({@code !name}) is kept as written: the conversion applies it as Asciidoctor does,
     * and unsets the attribute.
     */
    public static Map<String, String> siteAttributes(Map<String, String> configured) {
        final Map<String, String> attributes = new LinkedHashMap<>();
        if (configured != null) {
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                final String key = entry.getKey();
                if (key == null || key.isBlank() || entry.getValue() == null) {
                    continue;
                }
                attributes.put(key, TRUE.equals(entry.getValue()) ? "" : entry.getValue());
            }
        }
        return attributes;
    }

    /**
     * Which files an include may read, from the safe mode the site renders its AsciiDoc pages with
     * ({@code quarkus.asciidoc.safe-mode}, default {@code SAFE}), so that the Markdown never shows what the page
     * refuses to show.
     */
    public static Includes includes(String safeMode) {
        final String mode = safeMode == null ? "" : safeMode.strip().toLowerCase(Locale.ROOT);
        if ("unsafe".equals(mode)) {
            return Includes.ANY;
        }
        if ("secure".equals(mode)) {
            return Includes.NONE;
        }
        return Includes.SITE;
    }

    /**
     * Converts the source file on disk, where the {@code include::} directives are resolved; a page that comes from a
     * dependency jar has no file there and gets no Markdown, as before the conversion moved here.
     */
    @Override
    public Optional<String> convert(MarkdownConversion conversion) {
        final Path path = conversion.source();
        if (path == null || path.getFileName() == null) {
            LOG.warnf("AsciiDoc to Markdown: no source file, skipping: %s", path);
            return Optional.empty();
        }
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("docname", removeExtension(path.getFileName().toString()));
        try {
            if (conversion.rootUrl() != null) {
                putIfPresent(attributes, "site-url", conversion.rootUrl().absolute());
                putIfPresent(attributes, "site-path", conversion.rootUrl().path());
            }
            final RoqUrl url = conversion.url();
            if (url != null) {
                putIfPresent(attributes, "page-url", url.absolute());
                putIfPresent(attributes, "page-path", url.path());
                // Cross references are site-absolute, as on the HTML page (quarkiverse/quarkus-roq#1158): a relative
                // link breaks when the Markdown is read at an address without a trailing slash.
                putIfPresent(attributes, "relfileprefix", RoqUrl.parentPath(url.absolute()));
            }
        } catch (RuntimeException e) {
            // Without them the cross references fall back to relative links, which break at an address without a
            // trailing slash (quarkiverse/quarkus-roq#1158), so the author should know.
            LOG.warnf(e, "AsciiDoc to Markdown: site attributes unavailable for %s, cross references stay relative", path);
        }
        return convert(path, conversion.siteDir(), attributes, frontMatterAttributes(conversion.data()),
                conversion.title(), conversion.crossReferenceSuffix());
    }

    /**
     * The page's {@code asciidoc-attributes} front matter map, which the AsciiDoc plugins hand to the renderer after the
     * site attributes, so that a page attribute wins over a site attribute of the same name. Values are written as text
     * from the raw map, as {@code AsciidocTemplateExtension} does for the page.
     */
    Map<String, String> frontMatterAttributes(JsonObject data) {
        final Map<String, String> attributes = new LinkedHashMap<>();
        if (data != null && data.getValue(RoqAsciidocKeys.ASCIIDOC_ATTRIBUTES) instanceof JsonObject map) {
            for (Map.Entry<String, Object> entry : map.getMap().entrySet()) {
                if (entry.getValue() != null) {
                    attributes.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        return attributes;
    }

    private void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    /**
     * Converts with cross references ending in {@link #DEFAULT_CROSS_REFERENCE_SUFFIX}.
     *
     * @see #convert(Path, Path, Map, Map, String, String)
     */
    public Optional<String> convert(Path source, Path siteDir, Map<String, String> pageAttributes,
            Map<String, String> frontMatterAttributes, String fallbackTitle) {
        return convert(source, siteDir, pageAttributes, frontMatterAttributes, fallbackTitle, null);
    }

    /**
     * @param source the AsciiDoc file on disk (front matter allowed)
     * @param siteDir the site directory, the boundary of the {@code include::} directives
     * @param pageAttributes attributes computed for this page ({@code page-url}, {@code docname},
     *        {@code relfileprefix}, ...); a site attribute of the same name wins
     * @param frontMatterAttributes the page's {@code asciidoc-attributes} front matter, which wins over the site
     *        attributes, as on the rendered page
     * @param fallbackTitle title to use when the document has no title line
     * @param crossReferenceSuffix what a cross reference to another page ends with; null for
     *        {@link #DEFAULT_CROSS_REFERENCE_SUFFIX}
     * @return the Markdown, or empty when the page cannot be converted
     */
    public Optional<String> convert(Path source, Path siteDir, Map<String, String> pageAttributes,
            Map<String, String> frontMatterAttributes, String fallbackTitle, String crossReferenceSuffix) {
        if (source == null || !Files.isRegularFile(source)) {
            LOG.warnf("AsciiDoc to Markdown: source is not a readable file, skipping: %s", source);
            return Optional.empty();
        }
        final Map<String, String> attributes = new LinkedHashMap<>();
        if (pageAttributes != null) {
            attributes.putAll(pageAttributes);
        }
        siteAttributes.forEach((name, value) -> putApiAttribute(attributes, name, value, false));
        if (frontMatterAttributes != null) {
            frontMatterAttributes.forEach((name, value) -> putApiAttribute(attributes, name, value, true));
        }
        // The HTML plugins set noheader because the layout shows the title; the Markdown has no layout and keeps its
        // heading.
        attributes.remove("noheader");
        // Asciidoctor renders a code block whose callout markers do not match the list after it, with a warning; the
        // parser fails on it unless told to ignore the mismatch. A site or page attribute of the same name wins.
        attributes.putIfAbsent("callout-mismatch", "ignore");
        try {
            final String content = RoqFrontMatterTemplateUtils
                    .stripFrontMatter(Files.readString(source, StandardCharsets.UTF_8));
            final Document document = new Parser(attributes).parse(source, content.lines().toList(),
                    new Parser.ParserContext(new SiteIncludeResolver(source.getParent(), siteDir, includes)));
            final Map<String, String> rendererAttributes = new LinkedHashMap<>(attributes);
            rendererAttributes.putIfAbsent("relfileprefix", DEFAULT_RELATIVE_PREFIX);
            rendererAttributes.put("relfilesuffix",
                    crossReferenceSuffix == null ? DEFAULT_CROSS_REFERENCE_SUFFIX : crossReferenceSuffix);
            // A macro the renderer does not know is written as text, as asciidoctor shows it on the page, rather than
            // losing the Markdown of a page whose prose reads as a macro (groupId:artifactId[:version]). A site can still
            // set the yupiik-renderer-githubflavoredmarkdown-unknownMacro attribute, which wins.
            final GithubFlavoredMarkdownRenderer renderer = new GithubFlavoredMarkdownRenderer(
                    new GithubFlavoredMarkdownRenderer.Configuration()
                            .setAttributes(rendererAttributes)
                            .setUnknownMacro(UnknownMacro.TEXT),
                    new MarkdownSibling());
            renderer.visit(withTitle(document, fallbackTitle));
            return Optional.of(renderer.result());
        } catch (IOException | RuntimeException e) {
            LOG.warnf(e, "AsciiDoc to Markdown: AsciiDoc conversion failed for %s, skipping", source);
            return Optional.empty();
        } catch (StackOverflowError e) {
            // The trace would be a thousand frames of the parser; the file name is what the author needs.
            LOG.warnf("AsciiDoc to Markdown: AsciiDoc conversion overflowed the stack for %s, skipping", source);
            return Optional.empty();
        }
    }

    /**
     * Sets an attribute the way Asciidoctor reads an API attribute: {@code !name} and {@code name!} unset it, and a
     * trailing {@code @}, which only lets the document header win, is dropped because a header attribute always wins
     * in the Markdown. A front matter name is lowercased as Asciidoctor does; a site attribute keeps its case, as the
     * AsciidoctorJ plugin passes it, so that a renderer option such as
     * {@code yupiik-renderer-githubflavoredmarkdown-unknownMacro} is still found.
     */
    private void putApiAttribute(Map<String, String> attributes, String name, String value, boolean lowercase) {
        String key = lowercase ? name.strip().toLowerCase(Locale.ROOT) : name.strip();
        if (key.endsWith("@")) {
            key = key.substring(0, key.length() - 1);
        }
        if (key.startsWith("!")) {
            attributes.remove(key.substring(1));
        } else if (key.endsWith("!")) {
            attributes.remove(key.substring(0, key.length() - 1));
        } else if (!key.isEmpty()) {
            attributes.put(key, value);
        }
    }

    /**
     * The document with the fallback title when it has no title line, as for a page whose title is in its YAML front
     * matter.
     */
    Document withTitle(Document document, String fallbackTitle) {
        final Header header = document.header(); // the parser always gives one, with an empty title when there is none
        if (fallbackTitle == null || fallbackTitle.isBlank() || !header.title().isBlank()) {
            return document;
        }
        return new Document(new Header(fallbackTitle, header.author(), header.revision(), header.attributes()),
                document.body());
    }

    /**
     * How the Markdown reads the model, for the renderer and its state alike. {@code relfileprefix} applies to relative
     * targets only: a site-absolute {@code xref:/docs/page.adoc[]} points at {@code /docs/page/index.md}. And the
     * Markdown has no table of contents, whatever the {@code toc} attribute says: its headings already give the
     * structure, and a list of section links before the content is only more text for an agent to read.
     */
    static final class MarkdownSibling extends VisitorSibling {

        @Override
        public String relativeFile(String file, ConditionalBlock.Context context) {
            return file.startsWith("/") ? file : super.relativeFile(file, context);
        }

        @Override
        public String tocPlacement(ConditionalBlock.Context context) {
            return null;
        }
    }

    /**
     * Resolves includes relative to the including file, then relative to the page directory, and refuses the files the
     * AsciidoctorJ plugin refuses for the page: every file in {@code SECURE} mode, a file outside the site directory in
     * {@code SAFE} mode (outside the page directory when the site directory is unknown). As that plugin does, an
     * include is first looked up as a classpath resource under its site-relative path, so a shared file that only a
     * theme jar or {@code src/main/resources} provides is found, then on disk. A missing or refused include is logged
     * and skipped so that the rest of the page is still converted.
     */
    static final class SiteIncludeResolver implements RelativeContentResolver {

        private final Path pageDir;
        private final Path siteDir;
        private final Includes includes;

        SiteIncludeResolver(Path pageDir, Path siteDir, Includes includes) {
            this.pageDir = (pageDir == null ? Path.of(".") : pageDir).toAbsolutePath().normalize();
            this.siteDir = siteDir == null ? null : siteDir.toAbsolutePath().normalize();
            this.includes = includes;
        }

        /**
         * The include as a classpath resource under its path relative to the site directory, the lookup the
         * AsciidoctorJ plugin makes first for the page.
         */
        private Optional<RelativeContentResolver.Resolved> fromClasspath(Path target, Charset charset) {
            final Path absolute = target.toAbsolutePath().normalize();
            final String resourcePath = StringPaths.toUnixPath(
                    siteDir != null && absolute.startsWith(siteDir) ? siteDir.relativize(absolute).toString()
                            : absolute.toString());
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return Optional.empty();
                }
                return Optional.of(new RelativeContentResolver.Resolved(target,
                        new String(in.readAllBytes(), charset == null ? StandardCharsets.UTF_8 : charset).lines()
                                .toList()));
            } catch (IOException e) {
                LOG.warnf(e, "AsciiDoc to Markdown: include '%s' could not be read from the classpath, continuing without it",
                        resourcePath);
                return Optional.of(new RelativeContentResolver.Resolved(target, List.of()));
            }
        }

        @Override
        public Optional<RelativeContentResolver.Resolved> resolve(Path parent, String ref, Charset charset) {
            final Path relative = Path.of(ref);
            final Path target;
            if (relative.isAbsolute()) {
                target = relative.normalize();
            } else if (parent != null && parent.getParent() != null) {
                target = parent.getParent().resolve(relative).normalize();
            } else {
                target = pageDir.resolve(relative).normalize();
            }
            if (includes == Includes.NONE) {
                LOG.warnf("AsciiDoc to Markdown: include '%s' refused in SECURE mode (from %s), continuing without it", ref,
                        parent == null ? pageDir : parent);
                return Optional.of(new RelativeContentResolver.Resolved(target, List.of()));
            }
            final Path boundary = siteDir == null ? pageDir : siteDir;
            if (includes == Includes.SITE && !target.toAbsolutePath().normalize().startsWith(boundary)) {
                LOG.warnf("AsciiDoc to Markdown: include '%s' is outside the site directory (from %s), continuing without it",
                        ref,
                        parent == null ? pageDir : parent);
                return Optional.of(new RelativeContentResolver.Resolved(target, List.of()));
            }
            final Optional<RelativeContentResolver.Resolved> resource = fromClasspath(target, charset);
            if (resource.isPresent()) {
                return resource;
            }
            if (!Files.isRegularFile(target)) {
                LOG.warnf("AsciiDoc to Markdown: include '%s' not found (from %s), continuing without it", ref,
                        parent == null ? pageDir : parent);
                return Optional.of(new RelativeContentResolver.Resolved(target, List.of()));
            }
            try {
                return Optional.of(new RelativeContentResolver.Resolved(target,
                        Files.readAllLines(target, charset == null ? StandardCharsets.UTF_8 : charset)));
            } catch (IOException e) {
                LOG.warnf(e, "AsciiDoc to Markdown: include '%s' could not be read, continuing without it", target);
                return Optional.of(new RelativeContentResolver.Resolved(target, List.of()));
            }
        }
    }
}
