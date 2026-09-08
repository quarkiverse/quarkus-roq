package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
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
        Files.write(indexFiles.resolve("image(preview).png"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.write(indexFiles.resolve("logo.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });

        Path html = tempDir.resolve("newsletter/38/index.html");
        Files.writeString(html,
                """
                        <p>See section(3) and part(foo) for details.</p>
                        <img src="./index_files/content"/>
                        <img src="./index_files/content(1)"/>
                        <a href="./index_files/content(14)">link</a>
                        <img src='./index_files/image(preview).png'/>
                        <img src="./index_files/logo.png"/>
                        """,
                StandardCharsets.UTF_8);

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(tempDir);

        assertThat(result.filesRenamed()).isEqualTo(3);
        assertThat(result.htmlFilesUpdated()).isEqualTo(1);

        assertThat(indexFiles.resolve("content")).exists();
        assertThat(indexFiles.resolve("content-1")).exists();
        assertThat(indexFiles.resolve("content-14")).exists();
        assertThat(indexFiles.resolve("image-preview.png")).exists();
        assertThat(indexFiles.resolve("logo.png")).exists();
        assertThat(indexFiles.resolve("content(1)")).doesNotExist();
        assertThat(indexFiles.resolve("content(14)")).doesNotExist();
        assertThat(indexFiles.resolve("image(preview).png")).doesNotExist();

        String updatedHtml = Files.readString(html, StandardCharsets.UTF_8);
        assertThat(updatedHtml).contains("<p>See section(3) and part(foo) for details.</p>");
        assertThat(updatedHtml).contains("index_files/content\"");
        assertThat(updatedHtml).contains("index_files/content-1\"");
        assertThat(updatedHtml).contains("index_files/content-14\"");
        assertThat(updatedHtml).contains("index_files/image-preview.png'");
        assertThat(updatedHtml).contains("index_files/logo.png\"");
    }

    @Test
    void throwsOnFileCollision() throws IOException {
        Path dir = tempDir.resolve("collision");
        Files.createDirectories(dir);
        Files.write(dir.resolve("content(1)"), new byte[] { 1 });
        Files.write(dir.resolve("content-1"), new byte[] { 2 });

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        assertThatThrownBy(() -> renamer.rename(dir))
                .isInstanceOf(FileAlreadyExistsException.class)
                .hasMessageContaining("content-1");
    }

    /**
     * Two different source files that both rename to the same target name.
     * e.g. "foo(bar)" -> "foo-bar" and "foo(-bar)" -> "foo--bar" don't collide,
     * but "a(b)" -> "a-b" and "a-b(" -> "a-b" both produce "a-b".
     * The pre-check must catch this before any file is touched.
     */
    @Test
    void throwsWhenTwoSourcesRenameToSameTarget() throws IOException {
        Path dir = tempDir.resolve("dup-target");
        Files.createDirectories(dir);
        // "img(1)" -> "img-1"  (open-paren becomes dash, close-paren dropped)
        // "img(1"  -> "img-1"  (open-paren becomes dash, no close-paren to drop)
        // Both slug to the same target "img-1".
        Files.write(dir.resolve("img(1)"), new byte[] { 1 });
        Files.write(dir.resolve("img(1"), new byte[] { 2 });

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        assertThatThrownBy(() -> renamer.rename(dir))
                .isInstanceOf(FileAlreadyExistsException.class)
                .hasMessageContaining("img-1");

        // Neither file should have been moved (pre-check fires before any rename)
        assertThat(dir.resolve("img(1)")).exists();
        assertThat(dir.resolve("img(1")).exists();
        assertThat(dir.resolve("img-1")).doesNotExist();
    }

    /**
     * An HTML file whose own name contains parentheses gets renamed.
     * After the rename the HTML pass must read from the new path, not the stale original.
     */
    @Test
    void renamesHtmlFileWithParenthesesAndUpdatesItsReferences() throws IOException {
        Path dir = tempDir.resolve("html-parens");
        Files.createDirectories(dir);

        // The HTML file itself has parens in its name
        Files.write(dir.resolve("image(1).png"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.writeString(dir.resolve("page(draft).html"),
                "<img src=\"image(1).png\"/>",
                StandardCharsets.UTF_8);

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(dir);

        assertThat(result.filesRenamed()).isEqualTo(2); // image(1).png and page(draft).html
        assertThat(result.htmlFilesUpdated()).isEqualTo(1);

        // Old paths gone, new paths exist
        assertThat(dir.resolve("page(draft).html")).doesNotExist();
        assertThat(dir.resolve("page-draft.html")).exists();
        assertThat(dir.resolve("image(1).png")).doesNotExist();
        assertThat(dir.resolve("image-1.png")).exists();

        // The reference inside the renamed HTML file was updated
        String updatedHtml = Files.readString(dir.resolve("page-draft.html"), StandardCharsets.UTF_8);
        assertThat(updatedHtml).contains("src=\"image-1.png\"");
        assertThat(updatedHtml).doesNotContain("image(1).png");
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
