package io.quarkiverse.roq.plugin.aliases.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code alias-static-conflict-site} (resource)
 * <p>
 * Features tested: build fails when an alias claims the path of a static file (a page attachment).
 */
@DisplayName("Roq Aliases - Alias conflicting with a static file path")
public class RoqAliasesStaticFileConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "alias-static-conflict-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("alias-static-conflict-site"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RoqPathConflictException.class);
                assertThat(e.getMessage())
                        .contains("Path conflict")
                        .contains("Duplicate path 'gallery/photo.txt' claimed by both ")
                        // the static file is described by its path on the file system, with the separator of the OS
                        .contains("static file '")
                        .contains(Path.of("alias-static-conflict-site", "content", "gallery", "photo.txt") + "'")
                        .contains("alias '/gallery/photo.txt' of page 'old-photo.html'");
            });

    @Test
    @DisplayName("Build fails with RoqPathConflictException")
    public void testAliasConflictingWithStaticFileFails() {
        // This test verifies that the build fails — the assertException above does the work
    }
}
