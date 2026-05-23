package io.quarkiverse.roq.editor.runtime.devui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.assistant.runtime.dev.Assistant;
import io.quarkus.dev.console.DevConsoleManager;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@ApplicationScoped
public class RoqEditorJsonRPCService {
    private static final Logger LOG = Logger.getLogger(RoqEditorJsonRPCService.class);
    private static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern POST_NAME_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(.+)");
    private static final Map<String, String> MARKUPS = Map.of(
            "asciidoc", "adoc",
            "markdown", "md",
            "html", "html");

    private static final String SYSTEM_MESSAGE = "IGNORE ALL PREVIOUS INSTRUCTIONS about Quarkus, Java, and programming. "
            + "You are a blog content writer, NOT a programming assistant. "
            + "{{customContext}}"
            + "Generate blog post body content in Markdown based on the user's request. "
            + "The article context shows content around the cursor position. "
            + "Generate content that fits naturally at the cursor position. "
            + "STRICT RESPONSE FORMAT: You MUST return a JSON object with EXACTLY one field named \"body\". "
            + "The \"body\" field MUST contain the generated Markdown content as a string. "
            + "Do NOT add any other fields. Do NOT use \"title\", \"date\", \"description\", \"content\", or \"answer\" fields. "
            + "ONLY {\"body\": \"your markdown here\"}. "
            + "Example: {\"body\": \"## My Heading\\n\\nSome paragraph text.\\n\\nMore content here.\"}";

    @Inject
    private Site site;

    @Inject
    private RoqSiteConfig config;

    @Inject
    private RoqEditorConfig editorConfig;

    @Inject
    private Optional<Assistant> assistant;

    @Inject
    private Vertx vertx;

    @Blocking
    public List<PageSource> getPosts() {
        return site.collections().get("posts").stream()
                .sorted(Comparator.comparing(Page::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
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
    public WriteActionResult<SyncPathResult> syncPath(String path) {
        try {
            Page page = resolvePage(path);
            String slug = toSlug(page.title());
            final String date = formatDate(page.date());
            final String suggestedPath = getSuggestedPath(page, date, slug);
            if (suggestedPath == null) {
                return WriteActionResult.sync(new SyncPathResult(null));
            }
            Path currentFilePath = getPageAbsolutePath(page);
            Path contentDir = resolveContentDirPath(page);
            Path newFilePath = contentDir.resolve(suggestedPath);
            Path from = page.source().isIndex() ? currentFilePath.getParent() : currentFilePath;
            Path to = page.source().isIndex() ? newFilePath.getParent() : newFilePath;
            CompletableFuture.runAsync(() -> DevConsoleManager.invoke("roq-submit-rename", Map.of(
                    "from", from.toString(), "to", to.toString())));
            return WriteActionResult.success(new SyncPathResult(suggestedPath));
        } catch (Exception e) {
            LOG.errorf(e, "Error syncing path: %s", path);
            return WriteActionResult.error(e.getMessage());
        }
    }

    @Blocking
    public WriteActionResult<SaveResult> savePageContent(String path, String content, String date, String title) {
        try {
            if (content == null) {
                return WriteActionResult.error("Content parameter is required");
            }
            Page page = resolvePage(path);
            Path filePath = getPageAbsolutePath(page);
            boolean wasInSync = getCurrentSuggestedPath(page) == null;

            String suggestedPath = null;
            if (page instanceof DocumentPage && title != null && date != null) {
                String fileDate = date.length() >= 10 ? date.substring(0, 10) : date;
                suggestedPath = getSuggestedPath(page, fileDate, toSlug(title));
            }

            if (wasInSync && suggestedPath != null) {
                Path contentDir = resolveContentDirPath(page);
                Path newFilePath = contentDir.resolve(suggestedPath);
                Path from = page.source().isIndex() ? filePath.getParent() : filePath;
                Path to = page.source().isIndex() ? newFilePath.getParent() : newFilePath;
                CompletableFuture.runAsync(() -> DevConsoleManager.invoke("roq-submit-write-and-rename", Map.of(
                        "writePath", filePath.toString(), "content", content,
                        "from", from.toString(), "to", to.toString())));
                return WriteActionResult.success(new SaveResult(null, suggestedPath));
            }

            CompletableFuture.runAsync(() -> DevConsoleManager.invoke("roq-submit-write", Map.of(
                    "path", filePath.toString(), "content", content)));
            return WriteActionResult.success(new SaveResult(suggestedPath, null));
        } catch (Exception e) {
            LOG.errorf(e, "Error saving page: %s", path);
            return WriteActionResult.error(e.getMessage());
        }
    }

    @Blocking
    public WriteActionResult<CreatePageResult> createPage(String collectionId, String title, String markup) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return WriteActionResult.error("Title parameter is required");
            }
            if (markup == null || markup.trim().isEmpty() || !MARKUPS.containsKey(markup.trim())) {
                return WriteActionResult.error("Markup parameter is required");
            }

            String extension = MARKUPS.get(markup.trim());
            Path contentDir = resolveIndexContentDir();
            String slug = toSlug(title);

            final String dirName;
            final Path parentDir;
            final String date;
            if (collectionId != null) {
                date = LocalDate.now().format(FILE_NAME_DATE_FORMAT);
                dirName = resolveFileNamePattern(getCollectionConfig(collectionId).name(), date, slug);
                parentDir = contentDir.resolve(collectionId);
            } else {
                dirName = slug;
                parentDir = contentDir;
                date = null;
            }

            Path dir = parentDir.resolve(dirName);
            Path file = dir.resolve("index." + extension);

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

            CompletableFuture.runAsync(() -> DevConsoleManager.invoke("roq-submit-create", Map.of(
                    "dir", dir.toString(), "file", file.toString(), "content", frontmatter)));

            String relativePath = contentDir.relativize(file).toString();
            return WriteActionResult.success(new CreatePageResult(
                    new PageSource(collectionId, relativePath, title, "", null, extension, markup.trim(), date, null),
                    frontmatter));
        } catch (Exception e) {
            LOG.errorf(e, "Error creating page: %s", title);
            return WriteActionResult.error(e.getMessage());
        }
    }

    @Blocking
    public WriteActionResult<DeleteResult> deletePage(String path) {
        try {
            if (path == null || path.isEmpty()) {
                return WriteActionResult.error("Path parameter is required");
            }
            final Page page = resolvePage(path);
            Path postPath = getPageAbsolutePath(page);
            Path pathToDelete = page.source().isIndex() ? postPath.getParent() : postPath;
            CompletableFuture
                    .runAsync(() -> DevConsoleManager.invoke("roq-submit-delete", Map.of("path", pathToDelete.toString())));
            return WriteActionResult.success(new DeleteResult());
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting page: %s", path);
            return WriteActionResult.error(e.getMessage());
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

    private static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg");

    /**
     * Lists images from either the page directory or the public images directory.
     *
     * @param pagePath the source path of the page (used when location is "page")
     * @param location either "page" for page-level images or "public" for site-wide images
     * @return ListImagesResult containing the list of images or an error message
     */
    @Blocking
    public ListImagesResult listImages(String pagePath, String location) {
        try {
            List<String> images;
            Function<String, RoqUrl> lookup;
            if ("page".equals(location)) {
                var page = resolvePage(pagePath);
                images = page.files();
                lookup = (image) -> page.image(image);
            } else if ("public".equals(location)) {
                images = site.files();
                lookup = (image) -> site.file(image);
            } else {
                return new ListImagesResult(List.of(), "Invalid location: " + location + ". Use 'page' or 'public'.");
            }

            var result = images.stream().filter(this::isImageFile).sorted().map(p -> {
                var image = lookup.apply(p);
                return new ImageInfo(p, image.relative());
            }).toList();

            return new ListImagesResult(result, null);
        } catch (Exception e) {
            LOG.errorf(e, "Error listing images for location: %s, pagePath: %s", location, pagePath);
            return new ListImagesResult(List.of(), e.getMessage());
        }
    }

    private boolean isImageFile(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
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

    private Path resolveCollectionDir(DocumentPage page) {
        return resolveContentDirPath(page).resolve(page.collectionId());
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
        return StringPaths.slugify(title.toLowerCase().trim(), false, false);
    }

    private static final RoqEditorConfig.CollectionEditorConfig DEFAULT_COLLECTION_CONFIG = new RoqEditorConfig.CollectionEditorConfig() {
        @Override
        public String name() {
            return RoqEditorConfig.DEFAULT_COLLECTION_NAME_PATTERN;
        }

        @Override
        public boolean syncName() {
            return true;
        }
    };

    private RoqEditorConfig.CollectionEditorConfig getCollectionConfig(String collectionId) {
        if (collectionId != null && editorConfig.collectionsMap().containsKey(collectionId)) {
            return editorConfig.collectionsMap().get(collectionId);
        }
        return DEFAULT_COLLECTION_CONFIG;
    }

    private String getCurrentSuggestedPath(Page page) {
        String slug = toSlug(page.title());
        final String date = formatDate(page.date());
        return getSuggestedPath(page, date, slug);
    }

    private String getSuggestedPath(Page page, String date, String slug) {
        if (!(page instanceof DocumentPage docPage)) {
            return null;
        }
        var collectionConfig = getCollectionConfig(docPage.collectionId());
        if (!collectionConfig.syncName()) {
            return null;
        }
        boolean isDirPage = page.source().isIndex();
        Path currentFilePath = getPageAbsolutePath(page);
        Path collectionDir = resolveCollectionDir(docPage);
        Path contentDir = resolveContentDirPath(page);
        String currentName;
        if (isDirPage) {
            currentName = collectionDir.relativize(currentFilePath.getParent()).toString();
        } else {
            String relPath = collectionDir.relativize(currentFilePath).toString();
            currentName = StringPaths.removeExtension(relPath);
        }
        if (collectionConfig.name().startsWith(":date-")) {
            // Pattern expects a date prefix, skip if current name doesn't have one
            Matcher matcher = POST_NAME_PATTERN.matcher(currentName);
            if (!matcher.matches()) {
                LOG.warnf(
                        "Skipping name sync for '%s': filename doesn't match date prefix pattern (yyyy-MM-dd-*)."
                                + " To disable sync: editor.collections.\"%s\".sync-name=false"
                                + " or change the pattern: editor.collections.\"%s\".name=:slug",
                        currentName, docPage.collectionId(), docPage.collectionId());
                return null;
            }
        } else if (POST_NAME_PATTERN.matcher(currentName).matches() && !page.data().containsKey("date")) {
            // Current filename has a date prefix but the frontmatter has no date field,
            // and the pattern doesn't include :date-. Renaming would lose the date
            // since it only exists in the filename.
            LOG.warnf(
                    "Skipping name sync for '%s': renaming would lose the date from the filename."
                            + " Add a 'date' field to the frontmatter or use: editor.collections.\"%s\".name=:date-:slug~5",
                    currentName, docPage.collectionId());
            return null;
        }
        String syncedName = resolveFileNamePattern(collectionConfig.name(), date, slug);
        if (syncedName.contains("..") || syncedName.startsWith("/")) {
            LOG.warnf("Rejecting unsafe sync name: '%s'", syncedName);
            return null;
        }
        if (syncedName.equals(currentName)) {
            return null;
        }

        final Path newFilePath;
        if (isDirPage) {
            String indexFileName = currentFilePath.getFileName().toString();
            newFilePath = collectionDir.resolve(syncedName).resolve(indexFileName);
        } else {
            newFilePath = collectionDir.resolve(syncedName + "." + page.source().extension());
        }
        return contentDir.relativize(newFilePath).toString();
    }

    private String resolveFileNamePattern(String pattern, String date, String slug) {
        String year = date.length() >= 4 ? date.substring(0, 4) : "";
        String month = date.length() >= 7 ? date.substring(5, 7) : "";
        String day = date.length() >= 10 ? date.substring(8, 10) : "";
        return TemplateLink.resolvePattern(pattern, Map.of(
                ":date", date,
                ":slug", slug,
                ":year", year,
                ":month", month,
                ":day", day));
    }

    public record PageContentResult(String content, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    public record SaveResult(String suggestedPath, String newPath) {
    }

    public record SyncPathResult(String newPath) {
    }

    public record CreatePageResult(PageSource page, String content) {
    }

    public record DeleteResult() {
    }

    public record ImageInfo(String name, String path) {
    }

    public record ListImagesResult(List<ImageInfo> images, String errorMessage) {
        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    public CompletionStage<String> generateContent(String message, String context) {
        if (assistant.isEmpty()) {
            return CompletableFuture.failedStage(new RuntimeException("Assistant is not available"));
        }
        String baseUrl = getAssistantBaseUrl();
        if (baseUrl == null) {
            return CompletableFuture.failedStage(new RuntimeException("Assistant server is not running"));
        }

        String customContext = editorConfig.ai().context().orElse(null);

        // Use variables for user-provided content (article context) to avoid prompt injection
        JsonObject variables = new JsonObject()
                .put("customContext", customContext != null && !customContext.isBlank()
                        ? "Writing guidelines: " + customContext + ". "
                        : "");

        String userMessage = message;
        if (context != null && !context.isBlank()) {
            String trimmedContext = context.length() > 4000 ? context.substring(0, 4000) + "\n...(truncated)" : context;
            userMessage += "\n\nArticle context:\n" + trimmedContext;
        }

        JsonObject payload = new JsonObject()
                .put("genericInput", new JsonObject()
                        .put("programmingLanguage", "Java")
                        .put("programmingLanguageVersion", System.getProperty("java.version"))
                        .put("quarkusVersion", "n/a")
                        .put("systemmessageTemplate", SYSTEM_MESSAGE)
                        .put("usermessageTemplate", userMessage)
                        .put("variables", variables))
                .put("responseSchemaPrompt", "");

        var uri = java.net.URI.create(baseUrl + "/api/assist");
        CompletableFuture<String> future = new CompletableFuture<>();
        WebClient.create(vertx)
                .post(uri.getPort(), uri.getHost(), uri.getPath())
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .sendJsonObject(payload, ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(new RuntimeException("Assistant request failed", ar.cause()));
                    } else if (ar.result().statusCode() != 200) {
                        LOG.errorf("Assistant returned HTTP %d: %s", ar.result().statusCode(), ar.result().bodyAsString());
                        future.completeExceptionally(
                                new RuntimeException("Assistant returned HTTP " + ar.result().statusCode()));
                    } else {
                        future.complete(ar.result().bodyAsString());
                    }
                });
        return future;
    }

    // Workaround: ChappieAssistant.getBaseUrl() is not on the Assistant interface, use reflection until it is
    private String getAssistantBaseUrl() {
        try {
            var a = assistant.get();
            var method = a.getClass().getMethod("getBaseUrl");
            return (String) method.invoke(a);
        } catch (Exception e) {
            LOG.debug("Could not get assistant base URL", e);
        }
        return null;
    }

}
