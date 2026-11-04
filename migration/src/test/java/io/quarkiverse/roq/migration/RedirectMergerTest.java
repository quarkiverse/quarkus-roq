package io.quarkiverse.roq.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RedirectMergerTest {

    @TempDir
    Path project;

    Path content;

    @BeforeEach
    void createContent() throws IOException {
        content = Files.createDirectories(project.resolve("content"));
    }

    @Test
    void mergesANewUrlRedirectIntoItsTargetAsAnAlias() throws IOException {
        write("redirects/guides/old-name/index.md", "---\nnewUrl: /guides/new-name\n---\n");
        write("guides/new-name.adoc", "---\ntitle: New name\n---\nBody\n");

        run();

        assertThat(read("guides/new-name.adoc"))
                .isEqualTo("---\ntitle: \"New name\"\naliases:\n- \"/guides/old-name\"\n---\nBody\n");
        assertThat(content.resolve("redirects/guides/old-name/index.md")).doesNotExist();
    }

    @Test
    void mergesAnAliasesRedirectAndKeepsExistingAliases() throws IOException {
        write("redirects/old.md", "---\naliases:\n  - /new\n---\n");
        write("new.md", "---\ntitle: New\naliases:\n  - /older\n---\nBody\n");

        run();

        assertThat(read("new.md"))
                .isEqualTo("---\ntitle: \"New\"\naliases:\n- \"/older\"\n- \"/old\"\n---\nBody\n");
        assertThat(content.resolve("redirects/old.md")).doesNotExist();
    }

    @Test
    void turnsASingleAliasIntoAList() throws IOException {
        write("redirects/old.md", "---\nnewUrl: /new\n---\n");
        write("new.md", "---\naliases: /older\n---\nBody\n");

        run();

        assertThat(read("new.md")).isEqualTo("---\naliases:\n- \"/older\"\n- \"/old\"\n---\nBody\n");
    }

    @Test
    void addsFrontMatterToATargetWithout() throws IOException {
        write("redirects/old.md", "---\nnewUrl: /new/\n---\n");
        write("new.html", "<p>Body</p>\n");

        run();

        assertThat(read("new.html")).isEqualTo("---\naliases:\n  - /old\n---\n<p>Body</p>\n");
    }

    @Test
    void doesNotDuplicateAnExistingAlias() throws IOException {
        write("redirects/old.md", "---\nnewUrl: /new\n---\n");
        String target = "---\ntitle: New\naliases:\n  - /old\n---\nBody\n";
        write("new.md", target);

        run();

        assertThat(read("new.md")).isEqualTo(target);
        assertThat(content.resolve("redirects/old.md")).doesNotExist();
    }

    @Test
    void keepsRedirectsToExternalUrls() throws IOException {
        write("redirects/old.md", "---\nnewUrl: https://example.org/\n---\n");

        run();

        assertThat(content.resolve("redirects/old.md")).exists();
    }

    @Test
    void keepsRedirectsWhoseTargetIsMissing() throws IOException {
        write("redirects/old.md", "---\nnewUrl: /missing\n---\n");

        run();

        assertThat(content.resolve("redirects/old.md")).exists();
    }

    @Test
    void keepsRedirectsWithoutATarget() throws IOException {
        write("redirects/plain.md", "No front matter\n");
        write("redirects/empty.md", "---\ntitle: Nothing\n---\n");

        run();

        assertThat(content.resolve("redirects/plain.md")).exists();
        assertThat(content.resolve("redirects/empty.md")).exists();
    }

    @Test
    void deletesARedirectThatConflictsWithAContentPage() throws IOException {
        write("redirects/guides/foo/index.md", "---\nnewUrl: /guides/bar\n---\n");
        write("guides/foo.adoc", "= Foo\n");
        write("guides/bar.adoc", "= Bar\n");

        run();

        assertThat(content.resolve("redirects/guides/foo/index.md")).doesNotExist();
        assertThat(read("guides/foo.adoc")).isEqualTo("= Foo\n");
        assertThat(read("guides/bar.adoc")).isEqualTo("= Bar\n");
    }

    @Test
    void deletesACircularRedirect() throws IOException {
        write("redirects/guides/foo/index.md", "---\nnewUrl: /guides/foo/\n---\n");
        String page = "---\nlink: /guides/foo/\n---\nFoo\n";
        write("guides/foo.md", page);

        run();

        assertThat(content.resolve("redirects/guides/foo/index.md")).doesNotExist();
        assertThat(read("guides/foo.md")).isEqualTo(page);
    }

    @Test
    void doesNothingWithoutARedirectsDirectory() throws IOException {
        write("page.md", "---\ntitle: Page\n---\n");

        assertThat(new RedirectMerger().merge(content)).isZero();
        assertThat(read("page.md")).isEqualTo("---\ntitle: Page\n---\n");
    }

    @Test
    void countsMergedAndDeletedRedirects() throws IOException {
        write("redirects/merged.md", "---\nnewUrl: /target\n---\n");
        write("redirects/conflict.md", "---\nnewUrl: /target\n---\n");
        write("redirects/external.md", "---\nnewUrl: https://example.org/\n---\n");
        write("target.md", "Target\n");
        write("conflict.md", "Conflict\n");

        assertThat(new RedirectMerger().merge(content)).isEqualTo(2);
    }

    private void run() throws IOException {
        new RedirectMerger().merge(content);
    }

    private void write(String path, String text) throws IOException {
        Path file = content.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private String read(String path) throws IOException {
        return Files.readString(content.resolve(path));
    }
}
