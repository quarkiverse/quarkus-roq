package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Vetoed;

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
    private final RoqUrl imagesDirUrl;
    private final JsonObject data;
    private final List<NormalPage> pages;
    private final RoqCollections collections;
    private final LazyValue<Map<String, NormalPage>> pagesById;
    private final LazyValue<Map<String, DocumentPage>> documentsById;

    /**
     * @param url the Roq site url to the index page
     * @param imagesDirUrl directory to resolve images url (e.g. /static/images)
     * @param data the site FM data (declared in the index.html)
     * @param pages all the pages in this site (without the documents)
     * @param collections all the collections in this site (containing documents)
     */
    public Site(RoqUrl url, RoqUrl imagesDirUrl, JsonObject data, List<NormalPage> pages,
            RoqCollections collections) {
        this.url = url;
        this.imagesDirUrl = imagesDirUrl;
        this.data = data;
        this.pages = pages;
        this.collections = collections;
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
        return imagesDirUrl().resolve(img);
    }

    /**
     * Resolve an image url
     *
     * @param imageRelativePath the image relative path from the configured image dir
     */
    public RoqUrl image(Object imageRelativePath) {
        return imagesDirUrl().resolve(String.valueOf(imageRelativePath));
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
        return imagesDirUrl;
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
                Objects.equals(this.imagesDirUrl, that.imagesDirUrl) &&
                Objects.equals(this.data, that.data) &&
                Objects.equals(this.pages, that.pages) &&
                Objects.equals(this.collections, that.collections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, imagesDirUrl, data, pages, collections);
    }

    @Override
    public String toString() {
        return "Site[" +
                "url=" + url + ", " +
                "imagesDirUrl=" + imagesDirUrl + ", " +
                "data=" + data + ", " +
                "pages=" + pages + ", " +
                "collections=" + collections + ']';
    }

}
