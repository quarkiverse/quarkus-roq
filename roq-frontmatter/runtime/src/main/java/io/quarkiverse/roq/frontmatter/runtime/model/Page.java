package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.toUnixPath;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;
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
     * The page unique identifier, it is either the source file relative path (e.g. posts/my-post.md or a generated source path
     * for dynamic pages).
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
     * If it's a page with files, it will return from it else from the default image directory.
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
        if (info().usePublicFiles()) {
            // Use site static files
            return Arc.container().beanInstanceSupplier(Site.class).get().get().image(imageRelativePath);
        }
        String path = String.valueOf(imageRelativePath);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = normaliseName(path);
        return file(path);
    }

    /**
     * The page files files
     */
    default List<String> files() {
        if (info().usePublicFiles()) {
            throw new RoqStaticFileException(
                    "Can't list attached files. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(this.sourcePath()));
        }
        return info().files();
    }

    /**
     * Check if a file is attached to this page
     */
    default boolean hasFile(Object name) {
        if (info().usePublicFiles()) {
            throw new RoqStaticFileException(
                    "Can't find file '%s' attached to the page. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(name, this.sourcePath()));
        }
        var f = normaliseName(name);
        return info().hasFile(f);
    }

    /**
     * Get a page attached static file url and check if it exists
     */
    default RoqUrl file(Object name) {
        if (info().usePublicFiles()) {
            throw new RoqStaticFileException(
                    "Can't find file '%s' attached to the page. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(name, this.sourcePath()));
        }
        final String dir = toUnixPath(Path.of(this.sourcePath()).getParent().toString());
        return resolveFile(this, name, "Can't find '%s' in  '" + dir + "' which has no attached static file.",
                "File '%s' not found in '" + dir + "' directory (found: %s).");
    }

    static RoqUrl resolvePublicFile(Page page, Object name) {
        return resolveFile(page, name, "No file found in the public dir.",
                "File '%s' not found in public dir (found: %s).");
    }

    static RoqUrl resolveFile(Page page, Object name, String missingResourceMessage,
            String notFoundMessage) {
        if (name == null) {
            return null;
        }
        if (!page.info().hasFiles()) {
            throw new RoqStaticFileException(missingResourceMessage.formatted(name));
        }
        final String f = normaliseName(name);
        if (page.info().hasFile(f)) {
            return page.url().resolve(f);
        } else {
            throw new RoqStaticFileException(notFoundMessage.formatted(name,
                    String.join(", ", page.info().files())));
        }
    }

    static String normaliseName(Object name) {
        final String clean = String.valueOf(name).replace("./", "");
        return clean;
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
