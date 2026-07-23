package io.quarkiverse.roq.plugin.l10n.asciidoc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class L10nAdocPoFileResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesPoFileFromRelativePath() throws IOException {
        Path poBaseDir = tempDir.resolve("po");
        Path poFile = poBaseDir.resolve("content/guides/getting-started.adoc.po");
        Files.createDirectories(poFile.getParent());
        Files.writeString(poFile, "");

        Path rootDir = tempDir.resolve("project");

        Optional<Path> result = L10nAdocPoFileResolver.resolve(
                poBaseDir,
                rootDir.resolve("content/guides").toString(),
                rootDir.toString(),
                "getting-started");

        assertTrue(result.isPresent());
        assertEquals(poFile, result.get());
    }

    @Test
    void returnsEmptyWhenPoFileDoesNotExist() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");

        Optional<Path> result = L10nAdocPoFileResolver.resolve(
                poBaseDir,
                rootDir.resolve("content/guides").toString(),
                rootDir.toString(),
                "nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void handlesNestedContentPaths() throws IOException {
        Path poBaseDir = tempDir.resolve("po");
        Path poFile = poBaseDir.resolve("content/guides/security/overview.adoc.po");
        Files.createDirectories(poFile.getParent());
        Files.writeString(poFile, "");

        Path rootDir = tempDir.resolve("project");

        Optional<Path> result = L10nAdocPoFileResolver.resolve(
                poBaseDir,
                rootDir.resolve("content/guides/security").toString(),
                rootDir.toString(),
                "overview");

        assertTrue(result.isPresent());
        assertEquals(poFile, result.get());
    }

    @Test
    void handlesRootLevelContent() throws IOException {
        Path poBaseDir = tempDir.resolve("po");
        Path poFile = poBaseDir.resolve("content/index.adoc.po");
        Files.createDirectories(poFile.getParent());
        Files.writeString(poFile, "");

        Path rootDir = tempDir.resolve("project");

        Optional<Path> result = L10nAdocPoFileResolver.resolve(
                poBaseDir,
                rootDir.resolve("content").toString(),
                rootDir.toString(),
                "index");

        assertTrue(result.isPresent());
        assertEquals(poFile, result.get());
    }

    @Test
    void returnsEmptyWhenBaseDirIsNotUnderRootDir() {
        Path poBaseDir = tempDir.resolve("po");
        Path rootDir = tempDir.resolve("project");
        // baseDir is outside rootDir — relativize would throw without the guard
        Path outsideBaseDir = tempDir.resolve("other-project/content");

        Optional<Path> result = L10nAdocPoFileResolver.resolve(
                poBaseDir,
                outsideBaseDir.toString(),
                rootDir.toString(),
                "some-doc");

        assertTrue(result.isEmpty());
    }
}
