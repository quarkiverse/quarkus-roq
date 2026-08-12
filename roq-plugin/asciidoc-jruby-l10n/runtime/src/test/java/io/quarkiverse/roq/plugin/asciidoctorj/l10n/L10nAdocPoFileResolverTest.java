package io.quarkiverse.roq.plugin.asciidoctorj.l10n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class L10nAdocPoFileResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void computesPoPathFromSourcePath() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        Path sourcePath = rootDir.resolve("content/guides/getting-started.adoc");

        Path result = L10nAdocPoFileResolver.computePoPath(poBaseDir, rootDir, sourcePath);

        assertEquals(poBaseDir.resolve("content/guides/getting-started.adoc.po"), result);
    }

    @Test
    void handlesNestedContentPaths() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        Path sourcePath = rootDir.resolve("content/guides/security/overview.adoc");

        Path result = L10nAdocPoFileResolver.computePoPath(poBaseDir, rootDir, sourcePath);

        assertEquals(poBaseDir.resolve("content/guides/security/overview.adoc.po"), result);
    }

    @Test
    void handlesRootLevelContent() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        Path sourcePath = rootDir.resolve("content/index.adoc");

        Path result = L10nAdocPoFileResolver.computePoPath(poBaseDir, rootDir, sourcePath);

        assertEquals(poBaseDir.resolve("content/index.adoc.po"), result);
    }

    @Test
    void handlesAsciidocExtension() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        Path sourcePath = rootDir.resolve("content/guides/getting-started.asciidoc");

        Path result = L10nAdocPoFileResolver.computePoPath(poBaseDir, rootDir, sourcePath);

        assertEquals(poBaseDir.resolve("content/guides/getting-started.asciidoc.po"), result);
    }

    @Test
    void returnsNullWhenSourceIsOutsideRoot() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        // sourcePath is outside rootDir
        Path sourcePath = tempDir.resolve("other-project/content/some-doc.adoc");

        Path result = L10nAdocPoFileResolver.computePoPath(poBaseDir, rootDir, sourcePath);

        assertNull(result);
    }
}
