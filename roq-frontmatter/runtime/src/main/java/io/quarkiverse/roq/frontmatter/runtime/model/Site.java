package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Collection;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Roq site.
 *
 * @param url the Roq site url to the index page
 * @param imagesDirUrl directory to resolve images url (e.g. /static/images)
 * @param data the site FM data (declared in the index.html)
 * @param pages all the pages in this site (without the documents)
 * @param collections all the collections in this site (containing documents)
 */
@TemplateData
@Vetoed
public record Site(RoqUrl url, RoqUrl imagesDirUrl, JsonObject data, java.util.List<NormalPage> pages,
        RoqCollections collections) {

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
        return pages.stream().filter(p -> p.info().id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Find a document by id
     *
     * @param id the document page id (e.g. _posts/first-post)
     * @return the document page or null
     */
    public DocumentPage document(String id) {
        final List<DocumentPage> documents = collections().collections().values().stream()
                .flatMap(Collection::stream)
                .toList();
        return documents.stream().filter(d -> d.info().id().equals(id)).findFirst().orElse(null);
    }

}
