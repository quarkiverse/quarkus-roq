package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.*;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.*;
import static io.quarkiverse.tools.stringpaths.StringPaths.fileExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.FrontMatterTemplateMetadata;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.TemplateContext;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.ParsedHeaders;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.http.impl.MimeMapping;

public final class RoqFrontMatterScanUtils {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterScanUtils.class);

    private RoqFrontMatterScanUtils() {
    }

    // ── Ignored patterns & globs ────────────────────────────────────────

    /**
     * Build the combined list of ignored file patterns from {@link RoqSiteConfig#defaultIgnoredFiles()}
     * and {@link RoqSiteConfig#ignoredFiles()}, prefixing each with {@code glob:} if not already prefixed.
     */
    public static List<String> buildIgnoredPatterns(RoqSiteConfig config) {
        List<String> patterns = new ArrayList<>(config.defaultIgnoredFiles());
        config.ignoredFiles().ifPresent(patterns::addAll);
        return patterns.stream()
                .map(p -> p.startsWith("glob:") || p.startsWith("regex:") ? p : "glob:" + p)
                .toList();
    }

    /**
     * Build a glob pattern matching all template extensions (HTML output extensions + Qute suffixes).
     */
    public static String buildTemplateGlob(QuteConfig quteConfig) {
        Set<String> extensions = new HashSet<>(HTML_OUTPUT_EXTENSIONS);
        extensions.addAll(quteConfig.suffixes());
        return "glob:**.{" + String.join(",", extensions) + "}";
    }

    /**
     * Build a glob matching only HTML-output extensions.
     */
    public static String buildHtmlTemplateGlob() {
        return "glob:**.{" + String.join(",", HTML_OUTPUT_EXTENSIONS) + "}";
    }

    // ── Template predicates ─────────────────────────────────────────────

    public static Predicate<Path> isTemplate(QuteConfig config) {
        HashSet<String> suffixes = new HashSet<>(config.suffixes());
        suffixes.addAll(HTML_OUTPUT_EXTENSIONS);
        return path -> suffixes.contains(fileExtension(path.toString()));
    }

    public static boolean isTemplateTargetHtml(String path) {
        final String extension = fileExtension(path);
        return HTML_OUTPUT_EXTENSIONS.contains(extension);
    }

    // ── Output path resolution ──────────────────────────────────────────

    /**
     * Build the output path for a template: sanitize whitespace, strip extension, append resolved extension.
     */
    public static String resolveOutputPath(String referencePath, boolean hasMarkup, TemplateContext templateContext) {
        String cleanPath = replaceWhitespaceChars(referencePath);
        return removeExtension(cleanPath) + resolveOutputExtension(hasMarkup, templateContext);
    }

    public static String resolveOutputExtension(boolean markupFound,
            TemplateContext templateContext) {
        if (markupFound) {
            return ".html";
        }
        final String extension = templateContext.getExtension();
        if (extension == null) {
            return "";
        }
        final String mimeTypeForExtension = MimeMapping.getMimeTypeForExtension(extension);
        if (mimeTypeForExtension != null) {
            return "." + extension;
        }
        return ".html";
    }

    // ── Path utilities ──────────────────────────────────────────────────

    public static String deriveSiteDirPath(Path filePath, String relativePath) {
        Path resolved = filePath.normalize().toAbsolutePath();
        int depth = Path.of(relativePath).getNameCount();
        for (int i = 0; i < depth; i++) {
            resolved = Objects.requireNonNull(resolved.getParent(),
                    "Cannot derive site dir: relative path has more segments than file path");
        }
        return toUnixPath(resolved.toString());
    }

    static String replaceWhitespaceChars(String sourcePath) {
        return sourcePath.replaceAll("\\s+", "-");
    }

    public static void produceWatch(String watchPath, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (watchPath != null) {
            watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(watchPath).build());
        }
    }

    // ── Scanner query helpers ───────────────────────────────────────────

    /**
     * Query scanner for files under a subDir from all origins, using origin-split + merge
     * to handle non-root roqResourceDir for classpath resources.
     */
    public static List<ProjectFile> queryAllOrigins(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            String subDir,
            List<String> ignoredPatterns) throws IOException {
        List<ProjectFile> localFiles = scanner.query()
                .scopeDir(subDir)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .addExcluded(ignoredPatterns)
                .list();

        List<ProjectFile> resourceFiles = scanner.query()
                .scopeDir(roqProject.resolveRoqResourceSubDir(subDir))
                .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .addExcluded(ignoredPatterns)
                .list();

        return ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);
    }

    public static void scanSiteIndexAttachments(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterAttachment> attachments) throws IOException {

        final List<String> ignoredPatterns = buildIgnoredPatterns(config);

        // Static dir files (served under /static/ path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.staticDir(), ignoredPatterns)) {
            String attachmentName = config.staticDir() + "/" + toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new RoqFrontMatterAttachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }

        // Public dir files (served at root path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.publicDir(), ignoredPatterns)) {
            String attachmentName = toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new RoqFrontMatterAttachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }
    }

    /**
     * Order content files so that index templates (e.g. index.html) come first,
     * then other templates, then non-template files.
     */
    public static Comparator<ProjectFile> indexTemplatesFirst(Predicate<Path> templatePredicate) {
        return Comparator.comparingInt(f -> {
            String sp = toUnixPath(f.scopedPath());
            if (!templatePredicate.test(Path.of(sp)))
                return 2; // non-template (attachment candidate)
            return INDEX_FILES.contains(StringPaths.fileName(sp)) ? 0 : 1; // index template or other template
        });
    }

    /**
     * Walk up parent directories to find the nearest registered index that owns the given path.
     */
    public static Path findNearestOwner(Map<Path, List<RoqFrontMatterAttachment>> pageAttachments, Path path) {
        Path dir = path.getParent();
        while (dir != null) {
            if (pageAttachments.containsKey(dir)) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    // ── Parser config resolution ──────────────────────────────────────────

    /**
     * Resolve the {@link ParserConfig} for a scanned file based on its origin.
     * <p>
     * For local project files and application resources, the app-level
     * {@code quarkus.qute.alt-expr-syntax} config is used.
     * For dependency resources (themes), the pre-scanned {@code .qute} config map is consulted.
     */
    public static ParserConfig resolveParserConfig(ProjectFile file, QuteConfig quteConfig,
            Map<Path, ParserConfig> depParserConfigs) {
        if (file.origin() != ProjectFile.Origin.DEPENDENCY_RESOURCE) {
            return quteConfig.altExprSyntax()
                    ? TemplatePathBuildItem.ALT_PARSER_CONFIG
                    : ParserConfig.DEFAULT;
        }
        Path templateRoot = resolveTemplateRoot(file);
        if (templateRoot != null) {
            return depParserConfigs.getOrDefault(templateRoot, ParserConfig.DEFAULT);
        }
        return ParserConfig.DEFAULT;
    }

    /**
     * Derive the template root directory from a ProjectFile by stripping the scopedPath from the absolute path.
     */
    public static Path resolveTemplateRoot(ProjectFile file) {
        Path filePath = file.path().normalize().toAbsolutePath();
        int scopedDepth = Path.of(file.scopedPath()).getNameCount();
        Path root = filePath;
        for (int i = 0; i < scopedDepth; i++) {
            root = root.getParent();
            if (root == null) {
                return null;
            }
        }
        return root;
    }

    // ── Template metadata ────────────────────────────────────────────────

    /**
     * Collect metadata from a scanned file: compute paths, resolve markup/headers,
     * parse data, determine template ID, output path, and HTML characteristics.
     */
    public static FrontMatterTemplateMetadata collectMetadata(
            ProjectFile file, boolean isLayout,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            QuteConfig quteConfig,
            Map<Path, ParserConfig> depParserConfigs) {

        String referencePath = toUnixPath(file.scopedPath());
        String relativePath = toUnixPath(file.indexPath());
        String siteDirPath = deriveSiteDirPath(file.path(), relativePath);
        String fullContent = new String(file.content(), file.charset());

        SourceFile sourceFile = new SourceFile(siteDirPath != null ? siteDirPath : "", relativePath);

        TemplateContext templateContext = new TemplateContext(file.path(), referencePath, fullContent);
        RoqFrontMatterQuteMarkupBuildItem markup = RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter(markupList,
                templateContext);
        List<RoqFrontMatterHeaderParserBuildItem> headerParsers = RoqFrontMatterHeaderParserBuildItem
                .resolveHeaderParsers(headerParserList, templateContext);

        String templateId = resolveTemplateId(referencePath, isLayout);
        String outputPath = resolveOutputPath(referencePath, markup != null, templateContext);

        ParsedHeaders parsed = parseHeaders(headerParsers, templateContext, fullContent);

        boolean isHtml = isTemplateTargetHtml(referencePath);
        boolean isPartial = isHtmlPartial(parsed.content(), isHtml);

        ParserConfig parserConfig = resolveParserConfig(file, quteConfig, depParserConfigs);

        return new FrontMatterTemplateMetadata(file.path(), referencePath, sourceFile, markup, parsed,
                templateId, outputPath, isHtml, isPartial, parserConfig);
    }

}
