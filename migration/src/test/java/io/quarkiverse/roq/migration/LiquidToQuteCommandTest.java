package io.quarkiverse.roq.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiquidToQuteCommandTest {

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
        assertEquals("{#if page.title}<h1>{=page.title.raw}</h1>{/if}", postContent);
    }
}
