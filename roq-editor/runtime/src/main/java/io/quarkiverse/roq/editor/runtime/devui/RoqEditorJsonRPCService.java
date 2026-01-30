package io.quarkiverse.roq.editor.runtime.devui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.util.PathUtils;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class RoqEditorJsonRPCService {
    private static final Logger LOG = Logger.getLogger(RoqEditorJsonRPCService.class);
    private static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern POST_NAME_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(.+)");
    private static final Map<String, String> MARKUPS = Map.of(
            "asciidoc", "adoc",
            "markdown", "md",
            "html", "html");

    @Inject
    private Site site;

    @Inject
    private RoqSiteConfig config;

    @Blocking
    public List<PageSource> getPosts() {
        return site.collections().get("posts").stream()
                .sorted(Comparator.comparing(Page::date).reversed())
                .map(p -> new PageSource(p.collectionId(), p.sourcePath(), p.title(), p.description(), p.url().path(),
                        p.source().extension(), markup(p), formatDate(p.date()), getCurrentSuggestedPath(p)))
                .toList();
    }

    @Blocking
    public List<PageSource> getPages() {
        return site.pages().stream()
                .filter(p -> !p.source().generated())
                .map(p -> new PageSource(null, p.sourcePath(), p.title(), p.description(), p.url().path(),
                        p.source().extension(), markup(p), null, null))
                .toList();
    }

    @Blocking
    public PageContentResult getPageContent(String path) {
        try {
            final Path filePath = getPageAbsolutePath(resolvePage(path));
            return new PageContentResult(Files.readString(filePath, StandardCharsets.UTF_8), null);
        } catch (Exception e) {
            LOG.errorf(e, "Error reading source file for path: %s", path);
            return new PageContentResult(null, e.getMessage());
        }
    }

    @Blocking
    public SyncPathResult syncPath(String path) {

        try {
            Page page = resolvePage(path);
            String slug = toSlug(page.title());
            final String date = formatDate(page.date());
            final String suggestedPath = getSuggestedPath(page, date, slug);
            if (suggestedPath == null) {
                return new SyncPathResult(null, null);
            }
            Path currentFilePath = getPageAbsolutePath(page);
            Path contentDir = resolveContentDirPath(page);
            final Path newFilePath = contentDir.resolve(suggestedPath);
            final Path from;
            final Path to;
            if (page.source().isIndex()) {
                from = currentFilePath.getParent();
                to = newFilePath.getParent();
            } else {
                from = currentFilePath;
                to = newFilePath;
            }

            if (Files.exists(to)) {
                if (Files.isDirectory(to)) {
                    try {
                        Files.delete(to);
                    } catch (DirectoryNotEmptyException e) {
                        return new SyncPathResult(null, "Target dir already exists and is not empty: " + to);
                    }
                } else {
                    return new SyncPathResult(null, "Target already exists: " + to);
                }
            }

            Files.move(from, to);
            LOG.infof("Moved document from %s to %s", from, to);
            return new SyncPathResult(suggestedPath, null);
        } catch (Exception e) {
            LOG.errorf(e, "Error move document for path: %s", path);
            return new SyncPathResult(null, e.getMessage());
        }
    }

    @Blocking
    public SaveResult savePageContent(String path, String content, String date, String title) {
        if (content == null) {
            return new SaveResult(null, "Content parameter is required");
        }

        try {
            Page page = resolvePage(path);
            Path filePath = getPageAbsolutePath(page);

            String suggestedPath = null;
            if (page instanceof DocumentPage) {
                if (title != null && date != null) {
                    // Move post if date or title changed, returns new path and new file location
                    String newSlug = toSlug(title);
                    suggestedPath = getSuggestedPath(page, date, newSlug);
                }
            }

            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            LOG.infof("Successfully saved page: %s", filePath);

            return new SaveResult(suggestedPath, null);
        } catch (Exception e) {
            LOG.errorf(e, "Error saving page for path: %s", path);
            return new SaveResult(null, e.getMessage());
        }
    }

    @Blocking
    public CreatePageResult createPage(String collectionId, String title, String markup) {
        if (title == null || title.trim().isEmpty()) {
            return new CreatePageResult(null, null, "Title parameter is required");
        }

        if (markup == null || markup.trim().isEmpty() || !MARKUPS.containsKey(markup.trim())) {
            return new CreatePageResult(null, null, "Markup parameter is required");
        }

        String extension = MARKUPS.get(markup.trim());

        try {
            Path contentDir = resolveIndexContentDir();

            String slug = toSlug(title);

            final String dirName;
            final Path parentDir;
            final String date;
            if (collectionId != null) {
                date = LocalDate.now().format(FILE_NAME_DATE_FORMAT);
                dirName = date + "-" + slug;
                parentDir = contentDir.resolve(collectionId);
            } else {
                dirName = slug;
                parentDir = contentDir;
                date = null;
            }

            Path dir = parentDir.resolve(dirName);
            Path file = dir.resolve("index." + extension);

            Files.createDirectories(dir);

            String frontmatter = ("adoc".equals(extension) ? """
                    = %s
                    :date: %s
                    :description:
                    :image:

                    """ : """
                    ---
                    title: "%s"
                    date: "%s"
                    image: ""\s
                    description: ""\s
                    ---
                    """).formatted(title, FILE_NAME_DATE_FORMAT.format(LocalDate.now()));
            Files.writeString(file, frontmatter, StandardCharsets.UTF_8);

            String relativePath = contentDir.relativize(file).toString();
            LOG.infof("Successfully created post: %s", relativePath);
            return new CreatePageResult(
                    new PageSource(collectionId, relativePath, title, "", null, "md", markup.trim(), date, null), frontmatter,
                    null);
        } catch (Exception e) {
            LOG.errorf(e, "Error creating page with title: %s", title);
            return new CreatePageResult(null, null, e.getMessage());
        }
    }

    @Blocking
    public String deletePage(String path) {
        if (path == null || path.isEmpty()) {
            return "Path parameter is required";
        }

        try {
            final Page page = resolvePage(path);
            Path postPath = getPageAbsolutePath(page);

            // Delete the parent directory if this is an index.md file
            Path pathToDelete = page.source().isIndex()
                    ? postPath.getParent()
                    : postPath;

            if (!Files.exists(pathToDelete)) {
                return "Post not found at path: " + path;
            }

            if (Files.isDirectory(pathToDelete)) {
                deleteDirectory(pathToDelete);
            } else {
                Files.delete(pathToDelete);
            }

            LOG.infof("Successfully deleted post: %s", path);
            return "success";
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting post at path: %s", path);
            return e.getMessage();
        }
    }

    /**
     * Returns the configured date format from RoqSiteConfig.
     * This format is used for parsing and formatting dates in frontmatter.
     */
    @Blocking
    public String getDateFormat() {
        return config.dateFormat();
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            LOG.errorf(e, "Error deleting file: %s", path);
                        }
                    });
        }
    }

    private Path resolveIndexContentDir() {
        return resolveContentDirPath(site.index());
    }

    private Path resolveContentDirPath(Page page) {
        final Path siteDir = Path.of(page.source().file().siteDirPath()).toAbsolutePath().normalize();
        final Path contentDir = siteDir.resolve(config.contentDir());
        if (Files.isDirectory(contentDir)) {
            return contentDir;
        }
        throw new RuntimeException(
                "Unable to resolve a '%s' content' dir for path: '%s'".formatted(config.contentDir(), siteDir));
    }

    private static Path getPageAbsolutePath(Page page) {
        return Path.of(page.source().file().absolutePath()).toAbsolutePath().normalize();
    }

    /**
     * Resolves the absolute path for a given source path.
     */
    private Page resolvePage(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path parameter is required");
        }

        var page = site.page(path);
        if (page != null) {
            return page;
        }
        throw new RuntimeException("Path not found for page (retry in a few seconds): " + path);
    }

    private DocumentPage resolveDoc(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new Exception("Path parameter is required");
        }

        var page = site.document(path);
        if (page != null) {
            return page;
        }
        throw new RuntimeException("Path not found for page (retry in a few seconds): " + path);
    }

    private static String markup(Page page) {
        return page.source().markup() != null ? page.source().markup() : page.source().extension();
    }

    private String formatDate(ZonedDateTime date) {
        if (date == null) {
            return "";
        }
        return FILE_NAME_DATE_FORMAT.format(date);
    }

    /**
     * Converts a title to a URL-friendly slug.
     */
    private String toSlug(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return PathUtils.slugify(title.toLowerCase().trim(), false, false);
    }

    private String getCurrentSuggestedPath(Page page) {
        String slug = toSlug(page.title());
        final String date = formatDate(page.date());
        return getSuggestedPath(page, date, slug);
    }

    private String getSuggestedPath(Page page, String date, String slug) {
        if (page instanceof NormalPage) {
            return null;
        }
        boolean isDirPage = page.source().isIndex();
        Path currentFilePath = getPageAbsolutePath(page);
        String currentName = isDirPage ? currentFilePath.getParent().getFileName().toString()
                : PathUtils.removeExtension(currentFilePath.getFileName().toString());
        Matcher matcher = POST_NAME_PATTERN.matcher(currentName);
        if (!matcher.matches()) {
            return null;
        }
        String syncedName = date + "-" + slug;
        if (syncedName.equals(currentName)) {
            return null;
        }

        Path contentDir = resolveContentDirPath(page);
        final Path newFilePath;
        if (isDirPage) {
            String indexFileName = currentFilePath.getFileName().toString();
            newFilePath = currentFilePath.getParent().getParent().resolve(syncedName).resolve(indexFileName);
        } else {
            newFilePath = currentFilePath.getParent().resolve(syncedName + "." + page.source().extension());
        }
        return contentDir.relativize(newFilePath).toString();
    }

    public record PageContentResult(String content, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    public record SaveResult(String suggestedPath, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    public record SyncPathResult(String newPath, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    public record CreatePageResult(PageSource page, String content, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

}
