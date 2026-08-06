package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParenthesisFileRenamerTest {

    @TempDir
    Path tempDir;

    @Test
    void renamesFilesAndUpdatesHtml() throws IOException {
        Path indexFiles = tempDir.resolve("newsletter/38/index_files");
        Files.createDirectories(indexFiles);

        Files.write(indexFiles.resolve("content"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.write(indexFiles.resolve("content(1)"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.write(indexFiles.resolve("content(14)"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.write(indexFiles.resolve("logo.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });

        Path html = tempDir.resolve("newsletter/38/index.html");
        Files.writeString(html,
                "<img src=\"./index_files/content\"/>\n"
                        + "<img src=\"./index_files/content(1)\"/>\n"
                        + "<img src=\"./index_files/content(14)\"/>\n"
                        + "<img src=\"./index_files/logo.png\"/>\n",
                StandardCharsets.UTF_8);

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(tempDir);

        assertThat(result.filesRenamed()).isEqualTo(2);
        assertThat(result.htmlFilesUpdated()).isEqualTo(1);

        assertThat(indexFiles.resolve("content")).exists();
        assertThat(indexFiles.resolve("content-1")).exists();
        assertThat(indexFiles.resolve("content-14")).exists();
        assertThat(indexFiles.resolve("logo.png")).exists();
        assertThat(indexFiles.resolve("content(1)")).doesNotExist();
        assertThat(indexFiles.resolve("content(14)")).doesNotExist();

        String updatedHtml = Files.readString(html, StandardCharsets.UTF_8);
        assertThat(updatedHtml).contains("index_files/content\"");
        assertThat(updatedHtml).contains("index_files/content-1\"");
        assertThat(updatedHtml).contains("index_files/content-14\"");
        assertThat(updatedHtml).contains("index_files/logo.png\"");
        assertThat(updatedHtml).doesNotContain("content(");
    }

    @Test
    void noOpWhenNoParentheses() throws IOException {
        Path dir = tempDir.resolve("clean");
        Files.createDirectories(dir);
        Files.write(dir.resolve("image.png"), new byte[] { 0 });
        Files.writeString(dir.resolve("page.html"), "<img src=\"image.png\"/>", StandardCharsets.UTF_8);

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(tempDir);

        assertThat(result.filesRenamed()).isZero();
        assertThat(result.htmlFilesUpdated()).isZero();
    }

    @Test
    void handlesNonExistentDirectory() throws IOException {
        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(tempDir.resolve("does-not-exist"));

        assertThat(result.filesRenamed()).isZero();
        assertThat(result.htmlFilesUpdated()).isZero();
    }
}
