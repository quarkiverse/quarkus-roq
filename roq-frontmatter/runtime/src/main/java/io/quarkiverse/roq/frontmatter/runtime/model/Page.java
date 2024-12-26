package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateData;
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
        return data().getString(FM_TITLE, sourcePath());
    }

    /**
     * The page description (from FM data)
     */
    default String description() {
        return data().getString(FM_DESCRIPTION);
    }

    /**
     * The page image resolved url.
     * If it's a page with attachments, it will return from it else from the default image directory.
     */
    default RoqUrl image() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return image(img);
    }

    /**
     * Resolve an image url as an attachment or from the static image dir as a fallback
     *
     * @param imageRelativePath the image relative path as an attachment or from the configured image dir
     */
    default RoqUrl image(Object imageRelativePath) {
        if (imageRelativePath == null) {
            return null;
        }
        String path = String.valueOf(imageRelativePath);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = PathUtils.join(info().imagesDirPath(), path.replace("./", ""));
        if (hasAttachment(path)) {
            return attachment(path);
        }
        return Arc.container().beanInstanceSupplier(Site.class).get().get().url(path);
    }

    /**
     * The page attachments files
     */
    default List<String> attachments() {
        return info().attachments();
    }

    /**
     * Check if an attachment exists
     */
    default boolean hasAttachment(Object name) {
        if (name == null) {
            return false;
        }
        if (!info().hasAttachments()) {
            return false;
        }
        final String clean = String.valueOf(name).replace("./", "");
        return info().hasAttachments() && attachments().contains(clean);
    }

    /**
     * Get a page attachment url and check if it exists
     */
    default RoqUrl attachment(Object name) {
        if (name == null) {
            return null;
        }
        if (!info().hasAttachments()) {
            throw new RuntimeException("Can't find '%s' in '%s' which has no attachment.".formatted(name, sourcePath()));
        }
        final String clean = ((String) name).replace("./", "");
        if (info().hasAttachments() && attachments().contains(clean)) {
            return url().resolve(clean);
        } else {
            throw new RuntimeException("Attachment '%s' was not found for `%s` (%s)".formatted(name, sourcePath(),
                    String.join(",", attachments())));
        }
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
