package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterScanUtils.isInDraftDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Roq FrontMatter - Draft directory path matching")
public class IsInDraftDirectoryTest {

    @Test
    public void testNestedDraftDirectory() {
        assertTrue(isInDraftDirectory("posts/drafts/my-post.html", "drafts"));
    }

    @Test
    public void testDraftDirectoryAtRoot() {
        assertTrue(isInDraftDirectory("drafts/my-post.html", "drafts"));
    }

    @Test
    public void testSubstringDoesNotMatch() {
        assertFalse(isInDraftDirectory("posts/mydrafts/my-post.html", "drafts"));
    }

    @Test
    public void testSubstringPrefixDoesNotMatch() {
        assertFalse(isInDraftDirectory("draftsman/my-post.html", "drafts"));
    }

    @Test
    public void testCustomDraftDirectory() {
        assertTrue(isInDraftDirectory("posts/wip/my-post.html", "wip"));
        assertFalse(isInDraftDirectory("posts/drafts/my-post.html", "wip"));
    }

    @Test
    public void testDeeplyNestedPath() {
        assertTrue(isInDraftDirectory("blog/posts/drafts/2024/my-post.html", "drafts"));
    }

    @Test
    public void testNonDraftPath() {
        assertFalse(isInDraftDirectory("posts/published/my-post.html", "drafts"));
    }
}
