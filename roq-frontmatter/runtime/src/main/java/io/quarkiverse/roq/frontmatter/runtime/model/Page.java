package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.*;
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
     * The path of the source relative to the content directory (e.g posts/my-favorite-beer.md)
     */
    default String sourcePath() {
        return info().sourcePath();
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
     * Get the page default image which is attached to this page
     * or available in the public image dir for non directory pages.
     *
     * The image name is defined in the FM data with key `image` (or `img` or `picture`).
     *
     * @return the {@link RoqUrl} of the image or null if it is not defined.
     * @throws RoqStaticFileException if the image doesn't exist
     */
    default RoqUrl image() {
        final String img = getImgFromData(data());
        if (img == null) {
            return null;
        }
        return image(img);
    }

    default Site site() {
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
    default RoqUrl image(Object name) {
        if (name == null) {
            return null;
        }
        if (info().usePublicFiles()) {
            // Use site static files
            return site().image(name);
        }
        String path = String.valueOf(name);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = normaliseName(path, info().files().slugified());
        return file(path);
    }

    /**
     * Check if the image with the given name is attached to this page
     * or available in the public image dir for non directory pages.
     *
     * @param name the image name or relative path
     * @return true if it exists
     */
    default boolean imageExists(Object name) {
        if (name == null) {
            return false;
        }
        String path = String.valueOf(name);
        if (info().usePublicFiles()) {
            // Use site static files
            return site().imageExists(path);
        }
        path = normaliseName(path, info().files().slugified());
        return fileExists(path);
    }

    /**
     * The page attached list of files
     */
    default List<String> files() {
        if (info().usePublicFiles()) {
            throw new RoqStaticFileException(
                    "Can't list attached files. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(this.sourcePath()));
        }
        return info().files().names();
    }

    /**
     * Check if the file with the given name is attached to this page.
     *
     * @param name the file name or relative path
     */
    default boolean fileExists(Object name) {
        if (info().usePublicFiles()) {
            throw new RoqStaticFileException(
                    "Can't find file '%s' attached to the page. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(name, this.sourcePath()));
        }
        var f = normaliseName(name, info().files().slugified());
        return info().fileExists(f);
    }

    /**
     * Find the file attached to this page with the given name and return its static file url.
     *
     * @param name the file name or relative path
     * @return the {@link RoqUrl} of the file
     * @throws RoqStaticFileException if this page is not a directory page or if the file doesn't exist
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

    /**
     * The url to this page
     */
    RoqUrl url();

    /**
     * The FM data
     */
    JsonObject data();

    /**
     * Get the data value for a given key
     *
     * @param name the data key name
     * @return the value or null if not present
     */
    default Object data(String name) {
        if (data().containsKey(name)) {
            return data().getValue(name);
        }
        return null;
    }

}
