package io.quarkiverse.roq.editor.deployment.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.roq.editor.runtime.devui.EditorPaths;

class EditorPathsTest {

    @TempDir
    Path contentDir;

    @Test
    void resolvesDirectoryPage() {
        assertThat(EditorPaths.resolveNewPagePath(contentDir, "about/index.md", "md"))
                .isEqualTo(contentDir.resolve("about/index.md"));
    }

    @Test
    void resolvesFilePage() {
        assertThat(EditorPaths.resolveNewPagePath(contentDir, "about.md", "md"))
                .isEqualTo(contentDir.resolve("about.md"));
    }

    @Test
    void resolvesNestedPageAndNormalizesSeparators() {
        assertThat(EditorPaths.resolveNewPagePath(contentDir, " docs\\guides/intro.adoc ", "adoc"))
                .isEqualTo(contentDir.resolve("docs/guides/intro.adoc"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  ", "/about.md", "about/", "../about.md", "a/../../about.md", "a//about.md",
            "./about.md", "about", "about.adoc", ".md", "about/.md" })
    void rejectsInvalidPaths(String path) {
        assertThatThrownBy(() -> EditorPaths.resolveNewPagePath(contentDir, path, "md"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPath() {
        assertThatThrownBy(() -> EditorPaths.resolveNewPagePath(contentDir, null, "md"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canDeleteDirOfStandaloneIndexPage() {
        Path page = contentDir.resolve("about/index.md");
        assertThat(EditorPaths.canDeletePageDir(contentDir, page,
                List.of(page, contentDir.resolve("index.html"), contentDir.resolve("about-us/index.md")))).isTrue();
    }

    @Test
    void keepsContentDirWhenDeletingSiteIndex() {
        Path page = contentDir.resolve("index.html");
        assertThat(EditorPaths.canDeletePageDir(contentDir, page, List.of(page))).isFalse();
    }

    @Test
    void keepsDirHoldingOtherPages() {
        Path page = contentDir.resolve("docs/index.md");
        assertThat(EditorPaths.canDeletePageDir(contentDir, page,
                List.of(page, contentDir.resolve("docs/guides/intro.md")))).isFalse();
    }

    @Test
    void keepsDirOutsideContentDir() {
        Path page = contentDir.resolveSibling("other/index.md");
        assertThat(EditorPaths.canDeletePageDir(contentDir, page, List.of(page))).isFalse();
    }
}
