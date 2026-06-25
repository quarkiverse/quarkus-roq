package io.quarkiverse.roq.plugin.ogimage.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DESCRIPTION;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.IMAGE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.IMG;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.PICTURE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.TITLE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageConfig;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.vertx.core.json.JsonObject;

final class OgImageTargetResolver {

    private OgImageTargetResolver() {
    }

    record ResolvedTargets(Map<String, OgImageTarget> byRelativePath, Map<String, OgImageTarget> byPngPath) {
    }

    static ResolvedTargets resolve(
            OgImageConfig config,
            String siteName,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages) {
        if (!config.enabled()) {
            return new ResolvedTargets(Map.of(), Map.of());
        }

        List<String> collections = config.collections().orElse(List.of());
        List<String> includePaths = config.includePaths().orElse(List.of());
        if (collections.isEmpty() && includePaths.isEmpty()) {
            return new ResolvedTargets(Map.of(), Map.of());
        }

        String resolvedSiteName = siteName != null && !siteName.isBlank() ? siteName : config.siteName();
        Map<String, OgImageTarget> byRelativePath = new LinkedHashMap<>();
        Map<String, OgImageTarget> byPngPath = new LinkedHashMap<>();

        for (RoqFrontMatterPublishDocumentPageBuildItem document : documents) {
            if (document.collection().hidden()) {
                continue;
            }
            if (!collections.contains(document.collection().id())) {
                continue;
            }
            addTarget(config, resolvedSiteName, byRelativePath, byPngPath,
                    document.source().path().toString(),
                    document.url().resourcePath(),
                    document.collection().id(),
                    slugFromSource(document.source().id()),
                    document.data());
        }

        for (RoqFrontMatterPublishNormalPageBuildItem page : normalPages) {
            String resourcePath = page.url().resourcePath();
            if (!shouldIncludeNormalPage(config, resourcePath, page.source().isSiteIndex())) {
                continue;
            }
            addTarget(config, resolvedSiteName, byRelativePath, byPngPath,
                    page.source().path().toString(),
                    resourcePath,
                    null,
                    slugFromNormalPage(resourcePath, page.source().isSiteIndex()),
                    page.data());
        }

        return new ResolvedTargets(Map.copyOf(byRelativePath), Map.copyOf(byPngPath));
    }

    static OgImageTarget targetFromSource(OgImageConfig config, SourceData source, String pagePath, String collectionId,
            String slug, boolean siteIndex) {
        if (!config.enabled() || !source.isPage()) {
            return null;
        }
        if (config.skipIfImageSet() && hasImage(source.fm())) {
            return null;
        }
        if (isExcluded(config, pagePath)) {
            return null;
        }

        String title = firstNonBlank(source.fm().getString(TITLE), config.siteName());
        String description = firstNonBlank(source.fm().getString(DESCRIPTION), title);
        String kicker = source.fm().getString("kicker", "");
        String eyebrow = source.fm().getString("eyebrow", "");
        String pngPath = pngPath(config, pagePath, collectionId, slug);
        String imageAlt = imageAlt(config.siteName(), title, pagePath);

        return new OgImageTarget(
                pngPath,
                source.relativePath(),
                truncate(title, 120),
                truncate(description, 200),
                config.siteName(),
                kicker,
                eyebrow,
                imageAlt,
                config.width(),
                config.height());
    }

    static boolean matchesSource(OgImageConfig config, SourceData source, String pagePath, String collectionId,
            boolean siteIndex) {
        if (!config.enabled() || !source.isPage()) {
            return false;
        }
        if (isExcluded(config, pagePath)) {
            return false;
        }
        List<String> collections = config.collections().orElse(List.of());
        if (collectionId != null && collections.contains(collectionId)) {
            return true;
        }
        return shouldIncludeNormalPage(config, pagePath, siteIndex);
    }

    static PageContext pageContextFromSource(SourceData source) {
        String relativePath = source.relativePath().replace('\\', '/');
        String collectionId = source.collection() != null ? source.collection().id() : null;
        boolean siteIndex = relativePath.equals("index.md")
                || relativePath.equals("index.html")
                || relativePath.endsWith("/index.md")
                || relativePath.endsWith("/index.html");

        if (siteIndex && collectionId == null && (relativePath.equals("index.md") || relativePath.equals("index.html"))) {
            return new PageContext("/", null, "index", true);
        }

        if (collectionId != null) {
            String slug = slugFromRelativePath(relativePath);
            return new PageContext("/" + collectionId + "/" + slug + "/", collectionId, slug, false);
        }

        if (siteIndex) {
            String section = sectionFromRelativePath(relativePath);
            return new PageContext("/" + section + "/", null, section, false);
        }

        String slug = slugFromRelativePath(relativePath);
        return new PageContext("/" + slug + "/", null, slug, false);
    }

    record PageContext(String pagePath, String collectionId, String slug, boolean siteIndex) {
    }

    private static String slugFromRelativePath(String relativePath) {
        String name = relativePath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private static String sectionFromRelativePath(String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash <= 0) {
            return slugFromRelativePath(relativePath);
        }
        return relativePath.substring(0, slash);
    }

    private static boolean shouldIncludeNormalPage(OgImageConfig config, String resourcePath, boolean siteIndex) {
        List<String> includePaths = config.includePaths().orElse(List.of());
        if (includePaths.isEmpty()) {
            return false;
        }
        for (String includePath : includePaths) {
            if (matchesIncludePath(resourcePath, includePath, siteIndex)) {
                return !isExcluded(config, resourcePath);
            }
        }
        return false;
    }

    private static boolean matchesIncludePath(String resourcePath, String includePath, boolean siteIndex) {
        String normalizedInclude = normalizePath(includePath);
        String normalizedResource = normalizePath(resourcePath);
        if (normalizedInclude.equals("/") || normalizedInclude.isEmpty()) {
            return siteIndex || normalizedResource.equals("/");
        }
        return normalizedResource.equals(normalizedInclude)
                || normalizedResource.equals(trimTrailingSlash(normalizedInclude));
    }

    private static boolean isExcluded(OgImageConfig config, String resourcePath) {
        String normalizedResource = normalizePath(resourcePath);
        for (String exclude : config.excludePaths()) {
            String normalizedExclude = normalizePath(exclude);
            if (normalizedResource.startsWith(normalizedExclude)) {
                return true;
            }
        }
        return false;
    }

    private static void addTarget(
            OgImageConfig config,
            String siteName,
            Map<String, OgImageTarget> byRelativePath,
            Map<String, OgImageTarget> byPngPath,
            String sourcePath,
            String pagePath,
            String collectionId,
            String slug,
            JsonObject data) {
        if (config.skipIfImageSet() && hasImage(data)) {
            return;
        }
        if (isExcluded(config, pagePath)) {
            return;
        }

        String title = firstNonBlank(data.getString(TITLE), siteName);
        String description = firstNonBlank(data.getString(DESCRIPTION), title);
        String kicker = data.getString("kicker", "");
        String eyebrow = data.getString("eyebrow", "");
        String pngPath = pngPath(config, pagePath, collectionId, slug);
        String imageAlt = imageAlt(siteName, title, pagePath);

        OgImageTarget target = new OgImageTarget(
                pngPath,
                sourcePath,
                truncate(title, 120),
                truncate(description, 200),
                siteName,
                kicker,
                eyebrow,
                imageAlt,
                config.width(),
                config.height());

        byRelativePath.put(sourcePath, target);
        byPngPath.put(pngPath, target);
    }

    static String pngPath(OgImageConfig config, String pagePath, String collectionId, String slug) {
        String prefix = trimTrailingSlash(config.outputPrefix());
        if (prefix.isEmpty()) {
            prefix = "/og";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }

        String normalizedPagePath = normalizePath(pagePath);
        if (normalizedPagePath.equals("/")) {
            return prefix + "/index.png";
        }

        if (collectionId != null) {
            String segment = collectionOutputSegment(config, collectionId);
            return prefix + "/" + segment + "/" + slug + ".png";
        }

        return prefix + "/" + slug + ".png";
    }

    private static String collectionOutputSegment(OgImageConfig config, String collectionId) {
        String pattern = config.collectionOutputSegment();
        if (":collections".equals(pattern)) {
            return collectionId.endsWith("s") ? collectionId : collectionId + "s";
        }
        return pattern.replace(":collection", collectionId);
    }

    static String slugFromSource(String sourceId) {
        String name = sourceId;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private static String slugFromNormalPage(String resourcePath, boolean siteIndex) {
        if (siteIndex || normalizePath(resourcePath).equals("/")) {
            return "index";
        }
        String trimmed = trimTrailingSlash(normalizePath(resourcePath));
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    static String imageAlt(String siteName, String title, String pagePath) {
        if (normalizePath(pagePath).equals("/") && title.length() <= 140) {
            return title;
        }
        return siteName + " — " + truncate(title, 120);
    }

    static boolean hasImage(JsonObject data) {
        return data.containsKey(IMAGE) || data.containsKey(IMG) || data.containsKey(PICTURE);
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max - 1).trim() + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private static String trimTrailingSlash(String path) {
        if (path == null || path.length() <= 1) {
            return path;
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }
}
