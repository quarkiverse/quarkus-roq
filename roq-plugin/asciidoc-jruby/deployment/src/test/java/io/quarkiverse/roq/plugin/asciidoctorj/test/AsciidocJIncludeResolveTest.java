package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidocJInclude;

public class AsciidocJIncludeResolveTest {

    // ── resolveTargetPath ───────────────────────────────────────────────

    @ParameterizedTest(name = "target={2} => {3}")
    @CsvSource({
            // Same directory include
            "/site/content/guides, /site/content/guides, _attributes.adoc, /site/content/guides/_attributes.adoc",
            // Include going up directories
            "/site/content/guides, /site/content/guides, ../../includes/snippet.adoc, /site/includes/snippet.adoc",
            // Include in subdirectory
            "/site/content/guides, /site/content/guides, _includes/foo.adoc, /site/content/guides/_includes/foo.adoc",
            // Absolute target ignores baseDir
            "/site/content/guides, /site/content/guides, /absolute/path.adoc, /absolute/path.adoc",
    })
    void shouldResolveTargetPath(String baseDirStr, String dir, String target, String expected) {
        Path result = AsciidocJInclude.resolveTargetPath(Path.of(baseDirStr), dir, target);
        assertThat(result).isEqualTo(Path.of(expected));
    }

    // ── resolveResourcePath ─────────────────────────────────────────────

    @ParameterizedTest(name = "rootDir={0} targetPath={1} => {2}")
    @CsvSource({
            // Normal: targetPath under rootDir
            "/site, /site/content/guides/_attrs.adoc, content/guides/_attrs.adoc",
            // File directly under rootDir
            "/site, /site/file.adoc, file.adoc",
            // Deep nesting
            "/site, /site/a/b/c/d/file.adoc, a/b/c/d/file.adoc",
            // Similar prefix but different directory (Path.startsWith is component-wise)
            "/site, /site-other/file.adoc, /site-other/file.adoc",
            // targetPath outside rootDir entirely
            "/site, /other/file.adoc, /other/file.adoc",
    })
    void shouldResolveResourcePath(String rootDirStr, String targetPathStr, String expected) {
        String result = AsciidocJInclude.resolveResourcePath(Path.of(targetPathStr), Path.of(rootDirStr));
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldHandleEmptyRootDir() {
        String result = AsciidocJInclude.resolveResourcePath(
                Path.of("/site/content/file.adoc"), Path.of(""));
        assertThat(result).isEqualTo("/site/content/file.adoc");
    }

    @Test
    void shouldHandleTargetPathEqualsRootDir() {
        String result = AsciidocJInclude.resolveResourcePath(
                Path.of("/site"), Path.of("/site"));
        assertThat(result).isEmpty();
    }

    // ── resolveTargetPath + resolveResourcePath combined ────────────────

    @Test
    void shouldProduceValidClasspathPathForUpwardInclude() {
        Path baseDir = Path.of("/project/target/classes/content/guides");
        Path rootDir = Path.of("/project/target/classes");
        Path targetPath = AsciidocJInclude.resolveTargetPath(
                baseDir, baseDir.toString(), "../../includes/snippet.adoc");
        String resourcePath = AsciidocJInclude.resolveResourcePath(targetPath, rootDir);
        assertThat(resourcePath).isEqualTo("includes/snippet.adoc");
    }

    @Test
    void shouldFallBackToAbsolutePathWhenOutsideRootDir() {
        Path baseDir = Path.of("/project/target/classes/content/guides");
        Path rootDir = Path.of("/project/target/classes");
        Path targetPath = AsciidocJInclude.resolveTargetPath(
                baseDir, baseDir.toString(), "../../../outside/file.adoc");
        String resourcePath = AsciidocJInclude.resolveResourcePath(targetPath, rootDir);
        assertThat(resourcePath).isEqualTo("/project/target/outside/file.adoc");
    }

    @Test
    void shouldNotRelativizeTraversalAboveRootDir() {
        // ../../etc/passwd resolves above rootDir, so startsWith is false
        // and the absolute path is returned (which the security check would block before we get here)
        Path rootDir = Path.of("/site");
        Path targetPath = AsciidocJInclude.resolveTargetPath(
                Path.of("/site/content"), "/site/content", "../../etc/passwd");
        assertThat(targetPath).isEqualTo(Path.of("/etc/passwd"));
        assertThat(targetPath.startsWith(rootDir)).isFalse();
        String resourcePath = AsciidocJInclude.resolveResourcePath(targetPath, rootDir);
        // Falls back to absolute path, never relativized to "etc/passwd"
        assertThat(resourcePath).isEqualTo("/etc/passwd");
    }
}
