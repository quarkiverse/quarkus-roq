package io.quarkiverse.roq.frontmatter.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils;

/**
 * Pure unit tests for {@link RoqFrontMatterScanUtils#deriveSiteDirPath}.
 */
@DisplayName("Roq FrontMatter - deriveSiteDirPath")
public class DeriveSiteDirPathTest {

    @Test
    @DisplayName("Root-level file resolves to parent directory")
    public void testRootLevelFile() {
        Path filePath = Path.of("/site/content/index.html");
        String result = RoqFrontMatterScanUtils.deriveSiteDirPath(filePath, "index.html");
        assertEquals("/site/content", result);
    }

    @Test
    @DisplayName("Single-level nested file resolves correctly")
    public void testSingleLevelNested() {
        Path filePath = Path.of("/site/content/pages/about.html");
        String result = RoqFrontMatterScanUtils.deriveSiteDirPath(filePath, "pages/about.html");
        assertEquals("/site/content", result);
    }

    @Test
    @DisplayName("Directory index file resolves correctly")
    public void testDirectoryIndex() {
        Path filePath = Path.of("/site/content/pages/gallery/index.html");
        String result = RoqFrontMatterScanUtils.deriveSiteDirPath(filePath, "pages/gallery/index.html");
        assertEquals("/site/content", result);
    }

    @Test
    @DisplayName("Deeply nested index file resolves correctly")
    public void testDeeplyNestedIndex() {
        Path filePath = Path.of("/site/content/pages/gallery/sub/index.html");
        String result = RoqFrontMatterScanUtils.deriveSiteDirPath(filePath, "pages/gallery/sub/index.html");
        assertEquals("/site/content", result);
    }

    @Test
    @DisplayName("Collection document resolves correctly")
    public void testCollectionDocument() {
        Path filePath = Path.of("/site/content/posts/2024-03-10-my-post.html");
        String result = RoqFrontMatterScanUtils.deriveSiteDirPath(filePath, "posts/2024-03-10-my-post.html");
        assertEquals("/site/content", result);
    }
}
