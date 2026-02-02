package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.getImgFromData;
import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.normaliseName;
import static io.quarkiverse.roq.frontmatter.runtime.utils.Pages.resolvePublicFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Vetoed;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.impl.LazyValue;
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
    private final List<Page> allPages;

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
        this.page = pages.stream().filter(p -> p.source().isSiteIndex()).findFirst().orElseThrow();
        this.pagesById = new LazyValue<>(() -> pages.stream().collect(Collectors.toMap(NormalPage::id, Function.identity())));
        this.documentsById = new LazyValue<>(() -> collections().collections().values().stream()
                .flatMap(Collection::stream).collect(Collectors.toMap(DocumentPage::id, Function.identity(), (a, b) -> a)));
        this.allPages = getAllPages(pages, collections);
    }

    /**
     * @return The full list of pages for this site
     */
    public List<Page> allPages() {
        return allPages;
    }

    /**
     * @return the site index page.
     */
    public Page index() {
        return pages().stream()
                .filter(p -> p.source().isSiteIndex())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No index page found for site"));
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
     * Get the site default image from the public image dir.
     * <p>
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

    /**
     * Get the image with the given name from the public image dir.
     *
     * @param name the image name
     * @return the {@link RoqUrl} of the image (or path under the public image directory)
     * @throws RoqStaticFileException if the image doesn't exist
     */
    public RoqUrl image(Object name) {
        if (name == null) {
            return null;
        }
        String path = String.valueOf(name);
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        path = normaliseName(path, page.source().files().slugified());
        // Legacy images dir support
        if (fileExists(PathUtils.join("static/assets/images", path))) {
            return file(PathUtils.join("static/assets/images", path));
        }
        return file(PathUtils.join(imagesDir, path));
    }

    /**
     * Check if the image with the given name is available in the public image dir.
     *
     * @param name the image name (or path under the public image directory)
     * @return true if it exists
     */
    public boolean imageExists(String name) {
        String path = String.valueOf(name);
        path = normaliseName(path, page.source().files().slugified());
        // Legacy images dir support
        if (fileExists(PathUtils.join("static/assets/images", path))) {
            return true;
        }
        return fileExists(PathUtils.join(imagesDir, path));
    }

    /**
     * The site static files
     */
    public List<String> files() {
        return page.source().files().names();
    }

    /**
     * Check if the file with the given name is in the public directory.
     *
     * @param name the file name (or path under the public directory)
     */
    public boolean fileExists(Object name) {
        return page.source().fileExists(name);
    }

    /**
     * Find the file in the public directory with the given name and return its static file url.
     *
     * @param name the file name (or path under the public directory)
     * @return the {@link RoqUrl} of the file
     * @throws RoqStaticFileException if the file doesn't exist
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
     * Find a document by page path
     *
     * @param sourcePath the document source path (e.g. pages/first-page.html) or the generated source path for generated
     *        documents.
     * @return the document page or null
     */
    public DocumentPage document(String sourcePath) {
        return documentsById.get().get(sourcePath);
    }

    /**
     * @return The site url
     */
    public RoqUrl url() {
        return url;
    }

    /**
     * @return The image directory url
     */
    public RoqUrl imagesDirUrl() {
        return url().resolve(imagesDir);
    }

    /**
     * @return The site data
     */
    public JsonObject data() {
        return data;
    }

    /**
     * @return the full list of normal pages (without documents)
     */
    public List<NormalPage> pages() {
        return pages;
    }

    /**
     * @return the list of collections
     */
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
        return page.content();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        Site site = (Site) o;
        return Objects.equals(url, site.url) && Objects.equals(imagesDir, site.imagesDir) && Objects.equals(data, site.data)
                && Objects.equals(page, site.page)
                && Objects.equals(pageContentCache, site.pageContentCache) && Objects.equals(allPages, site.allPages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, imagesDir, data, page, pageContentCache, allPages);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Site.class.getSimpleName() + "[", "]")
                .add("url=" + url)
                .add("imagesDir='" + imagesDir + "'")
                .add("data=" + data)
                .add("page=" + page)
                .add("pageContentCache=" + pageContentCache)
                .add("allPages=" + allPages)
                .toString();
    }

    private static ArrayList<Page> getAllPages(List<NormalPage> pages, RoqCollections collections) {
        final ArrayList<Page> allPages = new ArrayList<>(pages);
        for (RoqCollection value : collections.collections().values()) {
            allPages.addAll(value);
        }
        return allPages;
    }

}
