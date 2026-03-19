package io.quarkiverse.roq.editor.runtime.devui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

@ApplicationScoped
public class RoqEditorImageResource {

    private static final Logger LOG = Logger.getLogger(RoqEditorImageResource.class);
    private static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg");
    private static final long MAX_UPLOAD_SIZE = 10 * 1024 * 1024; // 10MB

    @Inject
    Site site;

    @Inject
    RoqSiteConfig config;

    void init(@Observes Router router) {
        router.route("/q/roq-editor/api/upload-image")
                .method(HttpMethod.POST)
                .handler(BodyHandler.create()
                        .setBodyLimit(MAX_UPLOAD_SIZE)
                        .setHandleFileUploads(true)
                        .setUploadsDirectory(System.getProperty("java.io.tmpdir")))
                .handler(this::handleUpload);
    }

    private void handleUpload(RoutingContext ctx) {
        Path uploadedFile = null;
        try {
            var fileUploads = ctx.fileUploads();
            if (fileUploads == null || fileUploads.isEmpty()) {
                sendError(ctx, "No file provided");
                return;
            }

            var fileUpload = fileUploads.iterator().next();
            String filename = fileUpload.fileName();
            if (filename == null || filename.trim().isEmpty()) {
                sendError(ctx, "Filename is required");
                return;
            }

            String pagePath = ctx.request().getFormAttribute("pagePath");
            String location = ctx.request().getFormAttribute("location");

            String sanitizedFilename = sanitizeFilename(filename);
            if (!isValidImageFilename(sanitizedFilename)) {
                sendError(ctx, "Invalid image filename. Allowed extensions: " + String.join(", ", IMAGE_EXTENSIONS));
                return;
            }

            if (fileUpload.size() > MAX_UPLOAD_SIZE) {
                sendError(ctx, "Image size exceeds maximum allowed size of 10MB");
                return;
            }

            Path targetDir;
            String resultPath;

            if ("page".equals(location)) {
                if (pagePath == null || pagePath.isEmpty()) {
                    sendError(ctx, "Page path is required for page-level images");
                    return;
                }
                Page page = resolvePage(pagePath);
                if (page == null) {
                    sendError(ctx, "Page not found: " + pagePath);
                    return;
                }
                if (!page.source().isIndex()) {
                    sendError(ctx, "Page-level images are only supported for index pages");
                    return;
                }
                Path pageFilePath = getPageAbsolutePath(page);
                targetDir = pageFilePath.getParent();
                resultPath = sanitizedFilename;
            } else if ("public".equals(location)) {
                Path siteDir = resolveSiteDir();

                String imagesPath = config.imagesPath();
                if (imagesPath == null) {
                    imagesPath = "";
                }

                // Normalize for filesystem: ensure relative path and remove trailing slashes
                String fsImagesPath = imagesPath.replaceFirst("^/+", "").replaceAll("/+$", "");
                Path publicDirPath = siteDir.resolve(config.publicDir());
                if (fsImagesPath.isEmpty()) {
                    targetDir = publicDirPath;
                } else {
                    targetDir = publicDirPath.resolve(fsImagesPath);
                }
                Files.createDirectories(targetDir);

                // Normalize for URL: ensure exactly one leading slash and, if non-empty, one trailing slash
                String urlBasePath = imagesPath.trim();
                if (urlBasePath.isEmpty()) {
                    resultPath = "/" + sanitizedFilename;
                } else {
                    if (!urlBasePath.startsWith("/")) {
                        urlBasePath = "/" + urlBasePath;
                    }
                    if (!urlBasePath.endsWith("/")) {
                        urlBasePath = urlBasePath + "/";
                    }
                    resultPath = urlBasePath + sanitizedFilename;
                }
            } else {
                sendError(ctx, "Invalid location: " + location + ". Use 'page' or 'public'.");
                return;
            }

            Path targetFile = targetDir.resolve(sanitizedFilename);

            if (Files.exists(targetFile)) {
                sendError(ctx, "File already exists: " + sanitizedFilename);
                return;
            }

            uploadedFile = Path.of(fileUpload.uploadedFileName());
            Files.copy(uploadedFile, targetFile);

            LOG.infof("Successfully uploaded image: %s to %s", sanitizedFilename, targetDir);

            sendSuccess(ctx, resultPath);

        } catch (Exception e) {
            LOG.errorf(e, "Error uploading image");
            sendError(ctx, e.getMessage());
        } finally {
            if (uploadedFile != null) {
                try {
                    Files.deleteIfExists(uploadedFile);
                } catch (Exception ex) {
                    LOG.warnf(ex, "Failed to delete temporary uploaded file: %s", uploadedFile);
                }
            }
        }
    }

    private void sendSuccess(RoutingContext ctx, String path) {
        JsonObject response = new JsonObject()
                .put("path", path);
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    private void sendError(RoutingContext ctx, String message) {
        JsonObject response = new JsonObject()
                .put("errorMessage", message);
        if (ctx.response().getStatusCode() == 200) {
            ctx.response().setStatusCode(400);
        }
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    private String sanitizeFilename(String filename) {
        return filename.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");
    }

    private boolean isValidImageFilename(String filename) {
        String lower = filename.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private Page resolvePage(String path) {
        return site.page(path);
    }

    private Path resolveSiteDir() {
        return Path.of(site.index().source().file().siteDirPath()).toAbsolutePath().normalize();
    }

    private static Path getPageAbsolutePath(Page page) {
        return Path.of(page.source().file().absolutePath()).toAbsolutePath().normalize();
    }
}
