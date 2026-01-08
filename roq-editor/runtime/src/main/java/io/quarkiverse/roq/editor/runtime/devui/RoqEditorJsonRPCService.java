package io.quarkiverse.roq.editor.runtime.devui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.util.PathUtils;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class RoqEditorJsonRPCService {
    private static final Logger LOG = Logger.getLogger(RoqEditorJsonRPCService.class);
    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern POST_DIR_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(.+)");

    @Inject
    private Site site;

    @Blocking
    public List<Source> getPosts() {
        return site.allPages().stream()
                .filter(p -> p.sourcePath().startsWith("posts/"))
                .distinct()
                .sorted(Comparator.comparing(Page::date).reversed())
                .map(p -> new Source(p.sourcePath(), p.title(), p.description(), p.url().path(),
                        p.sourcePath(), formatDate(p.date())))
                .toList();
    }

    @Blocking
    public List<Source> getPages() {
        return site.pages().stream()
                .filter(p -> !p.sourcePath().startsWith("theme-layout"))
                .map(p -> new Source(p.sourcePath(), p.title(), p.description(), p.url().path(),
                        p.sourcePath(), formatDate(p.date())))
                .distinct().toList();
    }

    private String formatDate(ZonedDateTime date) {
        if (date == null) {
            return "";
        }
        return DATE_DISPLAY_FORMAT.format(date);
    }

    @Blocking
    public String getFileContent(String path) {
        try {
            Path filePath = resolvePagePath(path);
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.errorf(e, "Error reading source file for path: %s", path);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Resolves the absolute path for a given source path.
     */
    private Path resolvePagePath(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new Exception("Path parameter is required");
        }

        var page = site.page(path);
        if (page != null) {
            return Path.of(page.source().template().file().absolutePath());
        }

        // If not in site, check if it exists on disk
        Path contentDir = getSiteDirectory();
        if (contentDir != null) {
            Path file = contentDir.resolve(path);
            if (Files.exists(file)) {
                return file;
            }
        }
        throw new Exception("Path not found: " + path);
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

    /**
     * Parses a display date (e.g., "2025, Mar 24") to a file name date (e.g., "2025-03-24").
     */
    private String parseDisplayDate(String displayDate) {
        if (displayDate == null || displayDate.isBlank()) {
            return null;
        }
        try {
            var temporal = DATE_DISPLAY_FORMAT.parse(displayDate);
            return FILE_NAME_DATE_FORMAT.format(temporal);
        } catch (DateTimeParseException e) {
            LOG.warnf("Could not parse date: %s", displayDate);
            return null;
        }
    }

    /**
     * Moves a post directory when date or title changes.
     * Returns the new file path, or the original if no move was needed.
     */
    private Path movePostIfNeeded(Path currentFilePath, String newDate, String newSlug) throws IOException {
        Path currentDir = currentFilePath.getParent();
        String currentDirName = currentDir.getFileName().toString();

        Matcher matcher = POST_DIR_PATTERN.matcher(currentDirName);
        if (!matcher.matches()) {
            // Not a standard post directory, skip moving
            return currentFilePath;
        }

        String currentDate = matcher.group(1);
        String currentSlug = matcher.group(2);

        // Use current values if new ones are not provided
        String targetDate = (newDate != null && !newDate.isBlank()) ? newDate : currentDate;
        String targetSlug = (newSlug != null && !newSlug.isBlank()) ? newSlug : currentSlug;

        // Check if move is needed
        if (targetDate.equals(currentDate) && targetSlug.equals(currentSlug)) {
            return currentFilePath;
        }

        String newDirName = targetDate + "-" + targetSlug;
        Path newDir = currentDir.getParent().resolve(newDirName);

        if (Files.exists(newDir)) {
            throw new IOException("Target directory already exists: " + newDir);
        }

        Files.move(currentDir, newDir);
        LOG.infof("Moved post directory from %s to %s", currentDir, newDir);

        return newDir.resolve(currentFilePath.getFileName());
    }

    @Blocking
    public String saveFileContent(String path, String content, String date, String title) {
        if (content == null) {
            return "Error: Content parameter is required";
        }

        try {
            Path filePath = resolvePagePath(path);

            // Move post if date or title changed
            String newDate = parseDisplayDate(date);
            String newSlug = toSlug(title);
            filePath = movePostIfNeeded(filePath, newDate, newSlug);

            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            LOG.infof("Successfully saved file: %s", filePath);

            // Return the preview URL for this page
            var page = site.page(path);
            if (page != null) {
                return page.url().path();
            }
            return "success";
        } catch (Exception e) {
            LOG.errorf(e, "Error saving file for path: %s", path);
            return "Error: " + e.getMessage();
        }
    }

    @Blocking
    public String createPost(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Error: Title parameter is required";
        }

        try {
            Path siteDir = getSiteDirectory();
            if (siteDir == null) {
                return "Error: Could not determine site directory";
            }

            String slug = toSlug(title);
            String dateStr = LocalDate.now().format(FILE_NAME_DATE_FORMAT);
            String dirName = dateStr + "-" + slug;

            Path postDir = siteDir.resolve("posts").resolve(dirName);
            Path postFile = postDir.resolve("index.md");

            Files.createDirectories(postDir);

            String frontmatter = """
                    ---
                    title: "%s"
                    image:\s
                    description:\s
                    ---
                    """.formatted(title);
            Files.writeString(postFile, frontmatter, StandardCharsets.UTF_8);

            String relativePath = "posts/" + dirName + "/index.md";
            LOG.infof("Successfully created post: %s", relativePath);
            return relativePath;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating post with title: %s", title);
            return "Error: " + e.getMessage();
        }
    }

    @Blocking
    public String deletePost(String path) {
        if (path == null || path.isEmpty()) {
            return "Error: Path parameter is required";
        }

        try {
            Path postPath = resolvePagePath(path);

            // Delete the parent directory if this is an index.md file
            Path pathToDelete = "index.md".equals(postPath.getFileName().toString())
                    ? postPath.getParent()
                    : postPath;

            if (!Files.exists(pathToDelete)) {
                return "Error: Post not found at path: " + path;
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
            return "Error: " + e.getMessage();
        }
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

    private Path getSiteDirectory() {
        if (!site.pages().isEmpty()) {
            Page firstPage = site.pages().get(0);
            String absolutePath = firstPage.source().template().file().absolutePath();

            return Path.of(absolutePath).getParent();
        }
        return null;
    }
}
