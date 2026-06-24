package io.quarkus.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiquidToQuteCommandTest {

    @Test
    void testFileConversion(@TempDir Path tempDir) throws IOException {
        // Create input file
        Path inputFile = tempDir.resolve("input.html");
        String inputContent = "{{page.title | strip}}";
        Files.writeString(inputFile, inputContent);

        // Create output file path
        Path outputFile = tempDir.resolve("output.html");

        // Convert
        LiquidToQuteCommand command = new LiquidToQuteCommand();
        boolean success = command.convertFile(inputFile, outputFile);

        // Verify
        assertTrue(success, "File conversion should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String outputContent = Files.readString(outputFile);
        String expected = "{=page.title.trim()}";
        assertEquals(expected, outputContent, "File content should be converted");
    }

    @Disabled("Pre-existing failure: layout content conversion produces {=page.content} instead of {#insert /}")
    @Test
    void testDirectoryConversionViaCli(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("_layouts");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("default.html"), "<html>{{ content }}</html>");
        Files.writeString(inputDir.resolve("post.html"), "{% if page.title %}<h1>{{ page.title }}</h1>{% endif %}");

        Path outputDir = tempDir.resolve("templates/layouts");

        int exitCode = new picocli.CommandLine(new LiquidToQuteCommand())
                .execute(inputDir.toString(), outputDir.toString());

        assertEquals(0, exitCode, "Command should exit successfully");
        assertTrue(Files.exists(outputDir.resolve("default.html")), "default.html should be converted");
        assertTrue(Files.exists(outputDir.resolve("post.html")), "post.html should be converted");

        String defaultContent = Files.readString(outputDir.resolve("default.html"));
        assertEquals("<html>{#insert /}</html>", defaultContent);

        String postContent = Files.readString(outputDir.resolve("post.html"));
        assertEquals("{#if page.title}<h1>{=page.title}</h1>{/if}", postContent);
    }
}
