package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Roq site.
 */
@TemplateData
@Vetoed
public final class Site {
    private final RoqUrl url;
    private final String imagesDir;
    private final JsonObject data;
    private final List<NormalPage> pages;
    private final RoqCollections collections;
    private final LazyValue<Map<String, NormalPage>> pagesById;
    private final LazyValue<Map<String, DocumentPage>> documentsById;
    private final NormalPage page;

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
        path = path.replace("./", "");
        return staticFile(PathUtils.join(imagesDir, path));
    }

    /**
     * The site static files
     */
    public List<String> staticFiles() {
        return page.attachments();
    }

    /**
     * Check if a static file
     */
    public boolean hasStaticFile(Object name) {
        return page.hasAttachment(name);
    }

    /**
     * Get a static file url and check if it exists
     */
    public RoqUrl staticFile(Object name) {
        return page.attachment(name);
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
     * Find a page by id
     *
     * @param id the page id (e.g. pages/first-page.html)
     * @return the page or null
     */
    public NormalPage page(String id) {
        return pagesById.get().get(id);
    }

    /**
     * Find a document by id
     *
     * @param id the document page id (e.g. _posts/first-post)
     * @return the document page or null
     */
    public DocumentPage document(String id) {
        return documentsById.get().get(id);
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
