package io.quarkiverse.roq.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiquidToQuteDirectoryConverterTest {

    @Test
    void testFileConversion(@TempDir Path tempDir) throws IOException {
        // Create input file
        Path inputFile = tempDir.resolve("input.html");
        String inputContent = "{{page.title | strip}}";
        Files.writeString(inputFile, inputContent);

        // Create output file path
        Path outputFile = tempDir.resolve("output.html");

        // Convert
        boolean success = new LiquidToQuteDirectoryConverter(new LiquidToQuteConverter())
                .convertFile(inputFile, outputFile);

        // Verify
        assertTrue(success, "File conversion should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String outputContent = Files.readString(outputFile);
        String expected = "{=page.title.trim().raw}";
        assertEquals(expected, outputContent, "File content should be converted");
    }

    @Test
    void testDirectoryConversionKeepsLayoutAndSkipsOtherFiles(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("_includes");
        Files.createDirectories(inputDir.resolve("nested"));
        Files.writeString(inputDir.resolve("title.html"), "{{ page.title }}");
        Files.writeString(inputDir.resolve("nested/footer.html"), "{% if site.footer %}x{% endif %}");
        Files.writeString(inputDir.resolve("script.js"), "{{ not a template }}");
        Path outputDir = tempDir.resolve("templates/partials");

        LiquidToQuteDirectoryConverter.Result result = new LiquidToQuteDirectoryConverter(new LiquidToQuteConverter())
                .convertDirectory(inputDir, outputDir);

        assertEquals(new LiquidToQuteDirectoryConverter.Result(2, 0), result);
        assertEquals("{=page.title.raw}", Files.readString(outputDir.resolve("title.html")));
        assertEquals("{#if site.footer}x{/if}", Files.readString(outputDir.resolve("nested/footer.html")));
        assertFalse(Files.exists(outputDir.resolve("script.js")));
    }

    @Test
    void testNonRecursiveDirectoryConversion(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("_layouts");
        Files.createDirectories(inputDir.resolve("nested"));
        Files.writeString(inputDir.resolve("default.html"), "<html>{{ content }}</html>");
        Files.writeString(inputDir.resolve("nested/skipped.html"), "{{ page.title }}");
        Path outputDir = tempDir.resolve("out");

        new LiquidToQuteDirectoryConverter(new LiquidToQuteConverter())
                .recursive(false)
                .templateExtensions(List.of(".html"))
                .convertDirectory(inputDir, outputDir);

        assertTrue(Files.exists(outputDir.resolve("default.html")));
        assertFalse(Files.exists(outputDir.resolve("nested/skipped.html")));
    }
}
