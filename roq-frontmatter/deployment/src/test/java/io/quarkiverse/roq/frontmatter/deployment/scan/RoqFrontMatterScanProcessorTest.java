package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.isFileExcluded;
import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoqFrontMatterScanProcessorTest {

    private Path siteDir;

    @BeforeEach
    void setUp() {
        siteDir = Paths.get("/site");
    }

    @Test
    void shouldMatchDotDSStoreAnywhere() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("**.DS_Store"));

        assertThat(predicate.test(Paths.get("/site/.DS_Store"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/assets/.DS_Store"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/.hidden/.DS_Store"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/index.html"))).isFalse();
    }

    @Test
    void shouldMatchThumbsDbAnywhere() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("**Thumbs.db", "**Thumbs.db"));

        assertThat(predicate.test(Paths.get("/site/Thumbs.db"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/images/Thumbs.db"))).isTrue();
    }

    @Test
    void shouldMatchFilesInGitDirectory() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of(".git/**"));

        assertThat(predicate.test(Paths.get("/site/.git/config"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/.git/objects/abc"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/git/.config"))).isFalse();
    }

    @Test
    void shouldNotMatchNestedNodeModules() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("**node_modules/**"));

        assertThat(predicate.test(Paths.get("/site/node_modules/lodash.js"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/src/node_modules/lodash.js"))).isTrue();
    }

    @Test
    void shouldMatchMultiplePatterns() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("**.DS_Store", "**Thumbs.db"));

        assertThat(predicate.test(Paths.get("/site/assets/.DS_Store"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/assets/Thumbs.db"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/assets/image.png"))).isFalse();
    }

    @Test
    void shouldMatchGlobOnRelativePath() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("assets/**"));

        assertThat(predicate.test(Paths.get("/site/assets/css/style.css"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/assets/script.js"))).isTrue();
        assertThat(predicate.test(Paths.get("/site/scripts/script.js"))).isFalse();
    }

    @Test
    void shouldThrowIfPathNotInsideSiteDir() {
        Predicate<Path> predicate = isFileExcluded(siteDir, List.of("**.tmp"));

        assertThatThrownBy(() -> predicate.test(Paths.get("tmp/foo.tmp")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'other' is different type of Path");
    }

}
