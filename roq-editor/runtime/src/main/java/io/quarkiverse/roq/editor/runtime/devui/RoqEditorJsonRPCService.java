package io.quarkiverse.roq.editor.runtime.devui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class RoqEditorJsonRPCService {
    private static final Logger LOG = Logger.getLogger(RoqEditorJsonRPCService.class);

    @Inject
    private Site site;

    @Blocking
    public List<Source> getPosts() {
        return site.allPages().stream()
                .filter(p -> p.sourcePath().startsWith("posts/"))
                .distinct()
                .sorted(Comparator.comparing(Page::date).reversed())
                .map(p -> new Source(p.sourcePath(), p.title(), p.description(), p.url().path())).toList();
    }

    @Blocking
    public List<Source> getPages() {
        return site.pages().stream()
                .filter(p -> !p.sourcePath().startsWith("theme-layout"))
                .map(p -> new Source(p.sourcePath(), p.title(), p.description(), p.url().path())).distinct().toList();
    }

    @Blocking
    public String getFileContent(String path) {
        String filePath;
        try {
            filePath = getPagePath(path);
        } catch (Exception e) {
            return e.getMessage();
        }

        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.errorf(e, "Error reading source file for path: %s", filePath);
            return "Error reading source file: " + e.getMessage();
        }
    }

    private String getPagePath(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new Exception("Path parameter is required");
        }
        var page = site.page(path);

        // if it's not in the site it might be on disk
        if (page == null) {
            Path contentDir = getSiteDirectory();
            if (contentDir != null) {
                var file = contentDir.resolve(path);
                if (file.toFile().exists()) {
                    return file.toAbsolutePath().toString();
                }
            }
            throw new Exception("Path not found: " + path);
        }
        return page.source().template().file().absolutePath();
    }

    @Blocking
    public String saveFileContent(String path, String content) {
        if (content == null) {
            return "Error: Content parameter is required";
        }

        String filePath;
        try {
            filePath = getPagePath(path);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }

        try {
            Files.writeString(Path.of(filePath), content, StandardCharsets.UTF_8);
            LOG.infof("Successfully saved file: %s", filePath);

            // Return the preview URL for this page
            var page = site.page(path);
            if (page != null) {
                return page.url().path();
            }
            return "success";
        } catch (Exception e) {
            LOG.errorf(e, "Error saving source file for path: %s", filePath);
            return "Error: " + e.getMessage();
        }
    }

    @Blocking
    public String createPost(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Title parameter is required";
        }

        try {
            Path siteDir = getSiteDirectory();
            if (siteDir == null) {
                return "Error: Could not determine site directory";
            }

            String slug = title.toLowerCase().trim()
                    .replaceAll("\\s+", "-") // Replace whitespace with hyphens
                    .replaceAll("[^a-z0-9-]", "") // Remove special characters
                    .replaceAll("-+", "-") // Replace multiple hyphens with single hyphen
                    .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String relativePath = "posts/" + dateStr + "-" + slug + "/index.md";
            Path postDir = siteDir.resolve("posts").resolve(dateStr + "-" + slug);
            Path postFile = postDir.resolve("index.md");

            Files.createDirectories(postDir);

            // Create file with frontmatter template
            String frontmatter = "---\n" +
                    "title: \"" + title + "\"\n" +
                    "tags:\n" +
                    "---\n";
            Files.writeString(postFile, frontmatter, StandardCharsets.UTF_8);

            LOG.infof("Successfully created post: %s", relativePath);
            return relativePath;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating post with title: %s", title);
            return "Error creating post: " + e.getMessage();
        }
    }

    @Blocking
    public String deletePost(String path) {
        if (path == null || path.isEmpty()) {
            return "Path parameter is required";
        }

        try {
            String filePath;
            try {
                filePath = getPagePath(path);
            } catch (Exception e) {
                return e.getMessage();
            }

            Path postPath = Path.of(filePath);
            Path pathToDelete = postPath.getFileName().toString().equals("index.md")
                    ? postPath.getParent()
                    : postPath;

            if (!pathToDelete.toFile().exists()) {
                return "Error: Post not found at path: " + path;
            }

            if (pathToDelete.toFile().isDirectory()) {
                deleteDirectory(pathToDelete);
            } else {
                Files.delete(pathToDelete);
            }

            LOG.infof("Successfully deleted post: %s", path);
            return "success";
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting post at path: %s", path);
            return "Error deleting post: " + e.getMessage();
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
