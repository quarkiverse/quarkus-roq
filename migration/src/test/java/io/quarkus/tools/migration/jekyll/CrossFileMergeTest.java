package io.quarkus.tools.migration.jekyll;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateException;
import io.quarkus.tools.LiquidToQuteCommand;

class CrossFileMergeTest {

    @Test
    void partialPushInLoopCollapsesToMergeTypes(@TempDir Path tempDir) throws Exception {
        Path inputDir = copyTestFixtures(tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new picocli.CommandLine(new LiquidToQuteCommand())
                .execute(inputDir.toString(), outputDir.toString());
        assertEquals(0, exitCode, "Command should exit successfully");

        String partial = Files.readString(outputDir.resolve("_includes/merge-items.html"));
        assertTrue(partial.contains(".mergeTypes("),
                "Partial should have push-in-loop collapsed to mergeTypes(): " + partial);
        assertFalse(partial.contains(".push("),
                "Partial should not contain push() after collapse: " + partial);
    }

    @Test
    void parentIncludeCallerFixedToUseMergeTypes(@TempDir Path tempDir) throws Exception {
        Path inputDir = copyTestFixtures(tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new picocli.CommandLine(new LiquidToQuteCommand())
                .execute(inputDir.toString(), outputDir.toString());
        assertEquals(0, exitCode);

        String parent = Files.readString(outputDir.resolve("_includes/parent-docs.html"));

        assertFalse(parent.contains("{#include"),
                "Parent should not contain {#include} for collapsed partial: " + parent);
        assertTrue(parent.contains("mergeTypes('tutorial')"),
                "Parent should call mergeTypes('tutorial'): " + parent);
        assertTrue(parent.contains("mergeTypes('reference')"),
                "Parent should call mergeTypes('reference'): " + parent);
        assertFalse(parent.contains("{#if values}"),
                "Parent should not reference cross-scope 'values' variable: " + parent);
        assertFalse(parent.contains("values.orEmpty"),
                "Parent should not reference cross-scope 'values.orEmpty': " + parent);
    }

    @Test
    void parentOutputIsValidQute(@TempDir Path tempDir) throws Exception {
        Path inputDir = copyTestFixtures(tempDir);
        Path outputDir = tempDir.resolve("output");

        new picocli.CommandLine(new LiquidToQuteCommand())
                .execute(inputDir.toString(), outputDir.toString());

        String parent = Files.readString(outputDir.resolve("_includes/parent-docs.html"));

        Engine engine = Engine.builder().addDefaults().build();
        try {
            engine.parse(parent);
        } catch (TemplateException e) {
            fail("Parent output is not valid Qute:\n" + e.getMessage() + "\n\nOutput:\n" + parent);
        }
    }

    private Path copyTestFixtures(Path tempDir) throws Exception {
        Path fixtureDir = Paths.get(getClass().getClassLoader()
                .getResource("cross-file-merge/input/_includes/merge-items.html").toURI())
                .getParent().getParent();
        Path inputDir = tempDir.resolve("input");
        copyDirectory(fixtureDir, inputDir);
        return inputDir;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(s -> {
            Path t = target.resolve(source.relativize(s));
            try {
                if (Files.isDirectory(s)) {
                    Files.createDirectories(t);
                } else {
                    Files.createDirectories(t.getParent());
                    Files.copy(s, t);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
