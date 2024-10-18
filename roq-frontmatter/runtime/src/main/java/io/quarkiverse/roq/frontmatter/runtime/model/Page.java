package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Page (normal or document)
 */
@TemplateData
@Vetoed
public interface Page {

    /**
     * Page info
     */
    PageInfo info();

    /**
     * The page unique id, it is either the source file name (e.g. _posts/my-post.md or a generated id for dynamic pages).
     */
    default String id() {
        return info().id();
    }

    /**
     * The raw non rendered content
     */
    default String rawContent() {
        return info().rawContent();
    }

    /**
     * The file name without the extension (e.g. my-favorite-beer)
     */
    default String baseFileName() {
        return info().sourceBaseFileName();
    }

    /**
     * The file name (e.g my-favorite-beer.md)
     */
    default String sourceFileName() {
        return info().sourceFileName();
    }

    /**
     * The path of the source file (e.g posts/my-favorite-beer.md)
     */
    default String sourcePath() {
        return info().sourceFilePath();
    }

    /**
     * The publication date (from FM data or file-name)
     */
    default ZonedDateTime date() {
        return info().date();
    }

    /**
     * The page title (from FM data)
     */
    default String title() {
        return data().getString("title");
    }

    /**
     * The page description (from FM data)
     */
    default String description() {
        return data().getString("description");
    }

    /**
     * The page image resolved url
     */
    default RoqUrl img() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return Arc.container().beanInstanceSupplier(Site.class).get().get().url().resolve(info().imagesDirPath()).resolve(img);
    }

    /**
     * The url to this page
     */
    RoqUrl url();

    /**
     * The FM data
     */
    JsonObject data();

    default Object data(String name) {
        if (data().containsKey(name)) {
            return data().getValue(name);
        }
        return null;
    }

    static String getImgFromData(JsonObject data) {
        return data.getString("img", data.getString("image", data.getString("picture")));
    }
}
