package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DESCRIPTION;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.TITLE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_PAGE_CONTENT_FRAGMENT;
import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.getImgFromData;
import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.normaliseName;
import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.resolveFile;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.Vetoed;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;
import io.quarkiverse.roq.frontmatter.runtime.utils.SoftLazyValue;
import io.quarkus.arc.Arc;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Page (normal or document)
 */
@TemplateData
@Vetoed
public class Page {

    private static final Logger LOG = Logger.getLogger(Page.class);

    private final RoqUrl url;
    private final JsonObject data;
    private final PageSource source;
    private final SoftLazyValue<String> contentLazy = new SoftLazyValue<>(this::resolveContentLazy);
    private final SoftLazyValue<String> rawContentLazy = new SoftLazyValue<>(this::resolveRawContentLazy);
    private AtomicBoolean resolvingContent = new AtomicBoolean(false);

    protected Page(RoqUrl url, PageSource source, JsonObject data) {
        this.url = url;
        this.data = data;
        this.source = source;
    }

    /**
     * Page source
     */
    public PageSource source() {
        return source;
    }

    /**
     * Whether this page is marked as a draft.
     */
    public boolean draft() {
        return source().draft();
    }

    /**
     * The page unique identifier, it is either the source file relative path (e.g. posts/my-post.md or a generated source path
     * for dynamic pages).
     */
    public String id() {
        return source().id();
    }

    /**
     * Renders the inner content (without the layouts) of the given {@link Page} using the Qute template engine.
     */
    public String content() {
        if (resolvingContent.getAndSet(true)) {
            LOG.warnf("Recursive call to {page.content} detected in page: '%s'",
                    sourcePath());
            return ""; // or throw new IllegalStateException(...)
        }
        try {
            return contentLazy.get();
        } finally {
            resolvingContent.set(false);
        }
    }

    /**
     * The content of the file or template, without any frontmatter header or layouts.
     * If applicable, this includes the markup block (e.g. {@code <md>...</md>}).
     */
    public String rawContent() {
        return rawContentLazy.get();
    }

    private String resolveContentLazy() {
        try {
            final Engine engine = Arc.container().instance(Engine.class).get();
            final String id = source().template().generatedQuteTemplateId();
            final Template template = engine.getTemplate(id);
            if (template == null) {
                return "";
            }
            // Use the fragment for pages with a layout, or the full template otherwise
            final Template contentTemplate = template.getFragment(ROQ_PAGE_CONTENT_FRAGMENT);
            return (contentTemplate != null ? contentTemplate : template).render(Map.of(
                    "page", this,
                    "site", site()));

        } catch (Exception e) {
            LOG.warnf(e, "Failed to render page content for page: '%s'", sourcePath());
        }
        return "";
    }

    private String resolveRawContentLazy() {
        final String templateResource = "/templates/" + source().template().generatedQuteTemplateId();
        try (InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(templateResource)) {
            if (resource != null) {
                return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read '" + templateResource + "'", e);
        }
        return "";
    }

    /**
     * The file name without the extension (e.g. my-favorite-beer)
     */
    public String baseFileName() {
        return source().baseFileName();
    }

    /**
     * The file name (e.g my-favorite-beer.md)
     */
    public String sourceFileName() {
        return source().fileName();
    }

    /**
     * The path of the source relative to the content directory (e.g posts/my-favorite-beer.md)
     */
    public String sourcePath() {
        return source().path();
    }

    /**
     * The publication date (from FM data or file-name)
     * or null if not available
     */
    public ZonedDateTime date() {
        return source().date();
    }

    /**
     * The page title (`title` from FM data)
     */
    public String title() {
        return data().getString(TITLE, sourcePath());
    }

    /**
     * The page description (`description` from FM data)
     */
    public String description() {
        return data().getString(DESCRIPTION);
    }

    /**
     * Get the page default image which is attached to this page
     * or available in the public image dir for non directory pages.
     * <p>
     * The image name is defined in the FM data with key `image` (or `img` or `picture`).
     *
     * @return the {@link RoqUrl} of the image or null if it is not defined.
     * @throws RoqStaticFileException if the image doesn't exist
     */
    public RoqUrl image() {
        final String img = getImgFromData(data());
        if (img == null || img.isEmpty()) {
            return null;
        }
        return image(img);
    }

    public Site site() {
        return Arc.container().beanInstanceSupplier(Site.class).get().get();
    }

    /**
     * Get the image with the given name which is attached to this page
     * or available in the public image dir for non directory pages.
     *
     * @param name the image name or relative path
     * @return the {@link RoqUrl} of the image
     * @throws RoqStaticFileException if the image doesn't exist
     */
    public RoqUrl image(Object name) {
        if (name == null) {
            return null;
        }
        if (source().usePublicFiles()) {
            // Use site static files
            return site().image(name);
        }
        String path = String.valueOf(name);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = normaliseName(path, source().files().slugified());
        return file(path);
    }

    /**
     * Check if the image with the given name is attached to this page
     * or available in the public image dir for non directory pages.
     *
     * @param name the image name or relative path
     * @return true if it exists
     */
    public boolean imageExists(Object name) {
        if (name == null) {
            return false;
        }
        String path = String.valueOf(name);
        if (source().usePublicFiles()) {
            // Use site static files
            return site().imageExists(path);
        }
        path = normaliseName(path, source().files().slugified());
        return fileExists(path);
    }

    /**
     * The page attached list of files
     */
    public List<String> files() {
        if (source().usePublicFiles()) {
            throw new RoqStaticFileException(RoqException.builder("Cannot list attached files")
                    .source(this.source().template())
                    .detail("This page is not a directory page, so it cannot have attached files.")
                    .hint("Convert the page to a directory with an index file to allow attaching files."));
        }
        return source().files().names();
    }

    /**
     * Check if the file with the given name is attached to this page.
     *
     * @param name the file name or relative path
     */
    public boolean fileExists(Object name) {
        if (source().usePublicFiles()) {
            throw new RoqStaticFileException(RoqException.builder("Cannot find attached file")
                    .source(this.source().template())
                    .detail("Cannot check for file '%s' because this page is not a directory page.".formatted(name))
                    .hint("Convert the page to a directory with an index file to allow attaching files."));
        }
        var f = normaliseName(name, source().files().slugified());
        return source().fileExists(f);
    }

    /**
     * Find the file attached to this page with the given name and return its static file url.
     *
     * @param name the file name or relative path
     * @return the {@link RoqUrl} of the file
     * @throws RoqStaticFileException if this page is not a directory page or if the file doesn't exist
     */
    public RoqUrl file(Object name) {
        if (!source().isIndex()) {
            throw new RoqStaticFileException(RoqException.builder("Not a directory page")
                    .source(this.source().template())
                    .detail("Cannot attach file '%s' to this page.".formatted(name))
                    .hint("Only directory pages with an index can have attached files. Move the page into a directory with an index file."));
        }
        final Path path = Path.of(this.sourcePath());
        final String dir = path.getParent() == null ? "/" : toUnixPath(path.getParent().toString());
        return resolveFile(this, name, "the '%s' directory".formatted(dir));
    }

    /**
     * The url to this page
     */
    public RoqUrl url() {
        return url;
    }

    /**
     * The FM data
     */
    public JsonObject data() {
        return data;
    }

    /**
     * Get the data value for a given key
     *
     * @param name the data key name
     * @return the value or null if not present
     */
    public Object data(String name) {
        if (data().containsKey(name)) {
            return data().getValue(name);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        Page page = (Page) o;
        return Objects.equals(url, page.url) && Objects.equals(data, page.data) && Objects.equals(source, page.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, data, source);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Page.class.getSimpleName() + "[", "]")
                .add("url=" + url)
                .add("data=" + data)
                .add("source=" + source)
                .toString();
    }
}
