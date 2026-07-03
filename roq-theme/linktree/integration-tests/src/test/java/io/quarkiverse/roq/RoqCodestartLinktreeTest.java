package io.quarkiverse.roq;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class RoqCodestartLinktreeTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(QuarkusCodestartCatalog.Language.JAVA, QuarkusCodestartCatalog.Language.KOTLIN,
                    QuarkusCodestartCatalog.Language.SCALA)
            .setupStandaloneExtensionTest("io.quarkiverse.roq:quarkus-roq-theme-linktree")
            .putData("site", Map.of("title", "My Roq Site"))
            .build();

    @ParameterizedTest
    @EnumSource(QuarkusCodestartCatalog.Language.class)
    void testContent(QuarkusCodestartCatalog.Language language) throws Throwable {
        codestartTest.assertThatGeneratedFile(language, "data/profile.yml")
                .content()
                .contains("Ada Lovelace");
        codestartTest.assertThatGeneratedFile(language, "data/trees/my-links.yml")
                .content()
                .contains("Analytical Engine");
    }

    @Test
    void buildAll() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
