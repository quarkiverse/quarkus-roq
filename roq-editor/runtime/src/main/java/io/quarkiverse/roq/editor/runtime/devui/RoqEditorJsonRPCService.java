package io.quarkiverse.roq.editor.runtime.devui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

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
                .map(p -> new Source(p.sourcePath(), p.title(), p.description())).toList();
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

        if (page == null) {
            throw new Exception("Path not found: " + path);
        }
        return page.source().template().file().absolutePath();
    }

    @Blocking
    public String saveFileContent(String path, String content) {
        if (content == null) {
            return "Content parameter is required";
        }

        String filePath;
        try {
            filePath = getPagePath(path);
        } catch (Exception e) {
            return e.getMessage();
        }

        try {
            Files.writeString(Path.of(filePath), content, StandardCharsets.UTF_8);
            LOG.infof("Successfully saved file: %s", filePath);
            return "success";
        } catch (Exception e) {
            LOG.errorf(e, "Error saving source file for path: %s", filePath);
            return "Error saving source file: " + e.getMessage();
        }
    }
}
