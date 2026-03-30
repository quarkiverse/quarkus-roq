package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.TemplateContext;

/**
 * Pure unit tests for scanning utility methods in {@link RoqFrontMatterScanUtils}.
 */
@DisplayName("Roq FrontMatter - Scan utility methods")
public class RoqFrontMatterScanUtilsTest {

    // ── deriveSiteDirPath ───────────────────────────────────────────────

    // Expected site dir path, computed dynamically to be OS-independent (Windows prepends drive letter)
    private static final String SITE_CONTENT = toUnixPath(
            Path.of("/site/content").normalize().toAbsolutePath().toString());

    @Test
    @DisplayName("deriveSiteDirPath resolves root-level file to its parent")
    void deriveSiteDirPathRoot() {
        assertEquals(SITE_CONTENT,
                RoqFrontMatterScanUtils.deriveSiteDirPath(
                        Path.of("/site/content/index.html"), "index.html"));
    }

    @Test
    @DisplayName("deriveSiteDirPath resolves nested file to the content root")
    void deriveSiteDirPathNested() {
        assertEquals(SITE_CONTENT,
                RoqFrontMatterScanUtils.deriveSiteDirPath(
                        Path.of("/site/content/pages/about.html"), "pages/about.html"));
    }

    @Test
    @DisplayName("deriveSiteDirPath resolves directory index to the content root")
    void deriveSiteDirPathDirectoryIndex() {
        assertEquals(SITE_CONTENT,
                RoqFrontMatterScanUtils.deriveSiteDirPath(
                        Path.of("/site/content/pages/gallery/index.html"), "pages/gallery/index.html"));
    }

    @Test
    @DisplayName("deriveSiteDirPath resolves deeply nested file to the content root")
    void deriveSiteDirPathDeepNest() {
        assertEquals(SITE_CONTENT,
                RoqFrontMatterScanUtils.deriveSiteDirPath(
                        Path.of("/site/content/pages/gallery/sub/index.html"), "pages/gallery/sub/index.html"));
    }

    @Test
    @DisplayName("deriveSiteDirPath resolves collection document to the content root")
    void deriveSiteDirPathCollection() {
        assertEquals(SITE_CONTENT,
                RoqFrontMatterScanUtils.deriveSiteDirPath(
                        Path.of("/site/content/posts/2024-03-10-my-post.html"), "posts/2024-03-10-my-post.html"));
    }

    // ── isTemplateTargetHtml ────────────────────────────────────────────

    @Test
    @DisplayName("HTML files produce HTML output")
    void htmlIsTarget() {
        assertTrue(RoqFrontMatterScanUtils.isTemplateTargetHtml("pages/about.html"));
    }

    @Test
    @DisplayName("Markdown files produce HTML output")
    void mdIsTarget() {
        assertTrue(RoqFrontMatterScanUtils.isTemplateTargetHtml("posts/my-post.md"));
    }

    @Test
    @DisplayName("AsciiDoc files produce HTML output")
    void adocIsTarget() {
        assertTrue(RoqFrontMatterScanUtils.isTemplateTargetHtml("docs/guide.adoc"));
    }

    @Test
    @DisplayName("JSON files do not produce HTML output")
    void jsonIsNotTarget() {
        assertFalse(RoqFrontMatterScanUtils.isTemplateTargetHtml("data/beers.json"));
    }

    @Test
    @DisplayName("CSS files do not produce HTML output")
    void cssIsNotTarget() {
        assertFalse(RoqFrontMatterScanUtils.isTemplateTargetHtml("styles/main.css"));
    }

    // ── buildHtmlTemplateGlob ───────────────────────────────────────────

    @Test
    @DisplayName("buildHtmlTemplateGlob produces a glob matching all HTML-output extensions")
    void htmlTemplateGlobCoversAllExtensions() {
        String glob = RoqFrontMatterScanUtils.buildHtmlTemplateGlob();
        // Must be a valid glob pattern
        assertTrue(glob.startsWith("glob:"));
        // Must cover all content types that produce HTML
        for (String ext : RoqFrontMatterConstants.HTML_OUTPUT_EXTENSIONS) {
            assertTrue(glob.contains(ext),
                    "Glob should cover extension '%s'".formatted(ext));
        }
    }

    // ── resolveOutputExtension ──────────────────────────────────────────

    @Test
    @DisplayName("Markup-processed content always outputs as .html")
    void outputExtensionWithMarkup() {
        TemplateContext ctx = new TemplateContext(Path.of("test.md"), "test.md", "");
        assertEquals(".html", RoqFrontMatterScanUtils.resolveOutputExtension(true, ctx));
    }

    @Test
    @DisplayName("Known file types keep their original extension")
    void outputExtensionKnownMime() {
        TemplateContext ctx = new TemplateContext(Path.of("data.json"), "data.json", "");
        assertEquals(".json", RoqFrontMatterScanUtils.resolveOutputExtension(false, ctx));
    }

    @Test
    @DisplayName("HTML files without markup keep .html extension")
    void outputExtensionHtmlNoMarkup() {
        TemplateContext ctx = new TemplateContext(Path.of("page.html"), "page.html", "");
        assertEquals(".html", RoqFrontMatterScanUtils.resolveOutputExtension(false, ctx));
    }

    // ── resolveOutputPath ───────────────────────────────────────────────

    @Test
    @DisplayName("Output path preserves directory structure and extension")
    void outputPathBasic() {
        TemplateContext ctx = new TemplateContext(Path.of("pages/about.html"), "pages/about.html", "");
        assertEquals("pages/about.html",
                RoqFrontMatterScanUtils.resolveOutputPath("pages/about.html", false, ctx));
    }

    @Test
    @DisplayName("Markdown source outputs as .html when markup is applied")
    void outputPathMarkdown() {
        TemplateContext ctx = new TemplateContext(Path.of("posts/my-post.md"), "posts/my-post.md", "");
        assertEquals("posts/my-post.html",
                RoqFrontMatterScanUtils.resolveOutputPath("posts/my-post.md", true, ctx));
    }

    @Test
    @DisplayName("Whitespace in file names is replaced with hyphens")
    void outputPathWhitespace() {
        TemplateContext ctx = new TemplateContext(Path.of("pages/my page.html"), "pages/my page.html", "");
        assertEquals("pages/my-page.html",
                RoqFrontMatterScanUtils.resolveOutputPath("pages/my page.html", false, ctx));
    }

    // ── replaceWhitespaceChars ──────────────────────────────────────────

    @Test
    @DisplayName("Spaces are replaced with hyphens")
    void replaceSpaces() {
        assertEquals("my-page", RoqFrontMatterScanUtils.replaceWhitespaceChars("my page"));
    }

    @Test
    @DisplayName("Multiple consecutive spaces become a single hyphen")
    void replaceMultipleSpaces() {
        assertEquals("my-page", RoqFrontMatterScanUtils.replaceWhitespaceChars("my   page"));
    }

    @Test
    @DisplayName("Tabs are replaced with hyphens")
    void replaceTabs() {
        assertEquals("my-page", RoqFrontMatterScanUtils.replaceWhitespaceChars("my\tpage"));
    }

    // ── findNearestOwner ────────────────────────────────────────────────

    @Test
    @DisplayName("Finds the direct parent when it owns the path")
    void findDirectParent() {
        var owners = new java.util.HashMap<Path, java.util.List<RoqFrontMatterAttachment>>();
        owners.put(Path.of("pages"), new java.util.ArrayList<>());

        assertEquals(Path.of("pages"),
                RoqFrontMatterScanUtils.findNearestOwner(owners, Path.of("pages/photo.png")));
    }

    @Test
    @DisplayName("Walks up to find the nearest ancestor owner")
    void findAncestorOwner() {
        var owners = new java.util.HashMap<Path, java.util.List<RoqFrontMatterAttachment>>();
        owners.put(Path.of("pages"), new java.util.ArrayList<>());

        assertEquals(Path.of("pages"),
                RoqFrontMatterScanUtils.findNearestOwner(owners, Path.of("pages/sub/deep/photo.png")));
    }

    @Test
    @DisplayName("Returns null when no owner exists")
    void findNoOwner() {
        var owners = new java.util.HashMap<Path, java.util.List<RoqFrontMatterAttachment>>();

        assertNull(RoqFrontMatterScanUtils.findNearestOwner(owners, Path.of("orphan/photo.png")));
    }

}
