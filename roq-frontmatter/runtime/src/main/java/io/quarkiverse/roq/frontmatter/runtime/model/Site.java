package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.model.Page.normaliseName;
import static io.quarkiverse.roq.frontmatter.runtime.model.Page.resolvePublicFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Vetoed;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Roq site.
 */
@TemplateData
@Vetoed
public final class Site {

    private static final Logger LOG = Logger.getLogger(Site.class);

    private final RoqUrl url;
    private final String imagesDir;
    private final JsonObject data;
    private final List<NormalPage> pages;
    private final RoqCollections collections;
    private final LazyValue<Map<String, NormalPage>> pagesById;
    private final LazyValue<Map<String, DocumentPage>> documentsById;
    private final NormalPage page;
    private final Map<Page, String> pageContentCache = new ConcurrentHashMap<>();

    /**
     * @param url the Roq site url to the index page
     * @param imagesDir directory to resolve global images url (e.g. /static/images)
     * @param data the site FM data (declared in the index.html)
     * @param pages all the pages in this site (without the documents)
     * @param collections all the collections in this site (containing documents)
     */
    public Site(RoqUrl url, String imagesDir, JsonObject data, List<NormalPage> pages,
            RoqCollections collections) {
        this.url = url;
        this.imagesDir = imagesDir;
        this.data = data;
        this.pages = pages;
        this.collections = collections;
        this.page = pages.stream().filter(p -> p.info().isSiteIndex()).findFirst().get();
        this.pagesById = new LazyValue<>(() -> pages.stream().collect(Collectors.toMap(NormalPage::id, Function.identity())));
        this.documentsById = new LazyValue<>(() -> collections().collections().values().stream()
                .flatMap(Collection::stream).collect(Collectors.toMap(DocumentPage::id, Function.identity(), (a, b) -> a)));
    }

    public static Site getBeanInstance() {
        return Arc.container().beanInstanceSupplier(Site.class).get().get();
    }

    /**
     * The site title
     */
    public String title() {
        return data().getString("title");
    }

    /**
     * The site description
     */
    public String description() {
        return data().getString("description");
    }

    /**
     * The site image url if present
     */
    public RoqUrl image() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return image(img);
    }

    /**
     * Resolve an image url from the site static files (with check)
     *
     * @param imageRelativePath the image relative path under the configured image dir
     */
    public RoqUrl image(Object imageRelativePath) {
        if (imageRelativePath == null) {
            return null;
        }
        String path = String.valueOf(imageRelativePath);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = normaliseName(path, page.info().files().slugified());
        // Legacy images dir support
        if (hasFile(PathUtils.join("static/assets/images", path))) {
            return file(PathUtils.join("static/assets/images", path));
        }
        return file(PathUtils.join(imagesDir, path));
    }

    /**
     * The site static files
     */
    public List<String> files() {
        return page.info().files().names();
    }

    /**
     * Check if a static file
     */
    public boolean hasFile(Object name) {
        return page.info().hasFile(name);
    }

    /**
     * Get a static file url and check if it exists
     */
    public RoqUrl file(Object name) {
        return resolvePublicFile(page, name);
    }

    /**
     * Shortcut for url().resolve(path)
     */
    public RoqUrl url(Object path) {
        return this.url().resolve(path);
    }

    /**
     * Shortcut for url().resolve(path).resolve(path1)...
     */
    public RoqUrl url(Object path, Object path1) {
        return this.url(path).resolve(path1);
    }

    /**
     * Shortcut for url().resolve(path).resolve(path1)...
     */
    public RoqUrl url(Object path, Object path1, Object path2) {
        return this.url(path).resolve(path1).resolve(path2);
    }

    /**
     * Find a page or document page by source path
     *
     * @param sourcePath the page source path (e.g. pages/first-page.html) or the generated source path for generated pages (e.g
     *        /index_p2.html).
     * @return the page or null
     */
    public Page page(String sourcePath) {
        return pagesById.get().containsKey(sourcePath) ? pagesById.get().get(sourcePath) : documentsById.get().get(sourcePath);
    }

    /**
     * Find a normal page (documents not included) by source path
     *
     * @param sourcePath the page source path (e.g. pages/first-page.html) or the generated source path for generated pages (e.g
     *        /index_p2.html).
     * @return the page or null
     */
    public NormalPage normalPage(String sourcePath) {
        return pagesById.get().get(sourcePath);
    }

    /**
     * Find a document by sourcePath
     *
     * @param sourcePath the document source path (e.g. pages/first-page.html) or the generated source path for generated
     *        documents.
     * @return the document page or null
     */
    public DocumentPage document(String sourcePath) {
        return documentsById.get().get(sourcePath);
    }

    public RoqUrl url() {
        return url;
    }

    public RoqUrl imagesDirUrl() {
        return url().resolve(imagesDir);
    }

    public JsonObject data() {
        return data;
    }

    public List<NormalPage> pages() {
        return pages;
    }

    public RoqCollections collections() {
        return collections;
    }

    /**
     * Renders the inner content of the given {@link Page} using the Qute template engine.
     *
     * @param page the {@link Page} to render
     * @return the rendered content of the page
     */
    public String pageContent(Page page) {
        try {
            return pageContentCache.computeIfAbsent(page, p -> {
                final Engine engine = Arc.container().instance(Engine.class).get();
                return engine.parse(p.rawContent()).render(Map.of(
                        "page", p,
                        "site", this));

            });
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException && e.getMessage().contains("Recursive"))) {
                LOG.warnf(e, "Failed to render page content for file '%s'.", page.info().sourceFilePath());
            }
        }
        return "";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (Site) obj;
        return Objects.equals(this.url, that.url) &&
                Objects.equals(this.imagesDir, that.imagesDir) &&
                Objects.equals(this.data, that.data) &&
                Objects.equals(this.pages, that.pages) &&
                Objects.equals(this.collections, that.collections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, imagesDir, data, pages, collections);
    }

    @Override
    public String toString() {
        return "Site[" +
                "url=" + url + ", " +
                "imagesDirUrl=" + imagesDir + ", " +
                "data=" + data + ", " +
                "pages=" + pages + ", " +
                "collections=" + collections + ']';
    }

}
