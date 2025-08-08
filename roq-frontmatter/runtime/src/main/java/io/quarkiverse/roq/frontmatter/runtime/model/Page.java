package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.*;
import static io.quarkiverse.roq.util.PathUtils.toUnixPath;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

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
public class Page {

    public static final String FM_TITLE = "title";
    public static final String FM_DESCRIPTION = "description";

    private final RoqUrl url;
    private final JsonObject data;
    private final PageSource source;

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
     * The raw non rendered content
     */
    public String rawContent() {
        return source().rawContent();
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
     * The page title (from FM data)
     */
    public String title() {
        return data().getString(FM_TITLE, sourcePath());
    }

    /**
     * The page description (from FM data)
     */
    public String description() {
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
    public RoqUrl image() {
        final String img = getImgFromData(data());
        if (img == null) {
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
            throw new RoqStaticFileException(
                    "Can't list attached files. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(this.sourcePath()));
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
            throw new RoqStaticFileException(
                    "Can't find file '%s' attached to the page. Convert page '%s' to a directory (with an index) to allow attaching files."
                            .formatted(name, this.sourcePath()));
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
            throw new RoqStaticFileException(
                    "Only page directories with an index can have attached files. Then add '%s' to the page directory to fix this error."
                            .formatted(name, this.sourcePath()));
        }
        final Path path = Path.of(this.sourcePath());
        final String dir = path.getParent() == null ? "/" : toUnixPath(path.getParent().toString());
        return resolveFile(this, name, "Can't find '%s' in  '" + dir + "' which has no attached static file.",
                "File '%s' not found in '" + dir + "' directory (found: %s).");
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
