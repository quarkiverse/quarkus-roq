package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * This represents a Page (normal or document)
 */
@TemplateData
@Vetoed
public interface Page {

    String FM_TITLE = "title";
    String FM_DESCRIPTION = "description";

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
        return data().getString(FM_TITLE);
    }

    /**
     * The page description (from FM data)
     */
    default String description() {
        return data().getString(FM_DESCRIPTION);
    }

    /**
     * The page image resolved url
     */
    default RoqUrl image() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return Arc.container().beanInstanceSupplier(Site.class).get().get().image(img);
    }

    /**
     * Resolve an image url
     *
     * @param imageRelativePath the image relative path from the configured image dir
     */
    default RoqUrl image(Object imageRelativePath) {
        return Arc.container().beanInstanceSupplier(Site.class).get().get().image(String.valueOf(imageRelativePath));
    }

    /**
     * The page image resolved url from a given key
     *
     * @param key the data key containing the image relative path
     * @return the url
     */
    default Object dataAsImage(Object key) {
        final Object img = data().getValue(String.valueOf(key));
        if (img == null) {
            return null;
        }
        final Site site = Arc.container().beanInstanceSupplier(Site.class).get().get();
        if (img instanceof String imgString) {
            return site.image(imgString);
        }
        return null;
    }

    /**
     * The page image resolved url from a given key
     *
     * @param key the data key containing the image array of relative path
     * @return the list of urls
     */
    default List<RoqUrl> dataAsImages(Object key) {
        final Object img = data().getValue(String.valueOf(key));
        if (img instanceof JsonArray list) {
            return list.stream().filter(s -> s instanceof String).map(String::valueOf).map(this::image).toList();
        }
        return null;
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
