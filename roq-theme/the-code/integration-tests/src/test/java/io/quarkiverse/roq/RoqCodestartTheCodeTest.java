package io.quarkiverse.roq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

class RoqCodestartTheCodeTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(QuarkusCodestartCatalog.Language.JAVA, QuarkusCodestartCatalog.Language.KOTLIN,
                    QuarkusCodestartCatalog.Language.SCALA)
            .setupStandaloneExtensionTest("io.quarkiverse.roq:quarkus-roq-theme-the-code")
            .build();

    @ParameterizedTest
    @EnumSource(QuarkusCodestartCatalog.Language.class)
    void testContent(QuarkusCodestartCatalog.Language language) throws Throwable {
        codestartTest.assertThatGeneratedFile(language, "content/index.html")
                .content().contains("Hi, I'm Your Name!");
        codestartTest.assertThatGeneratedFile(language, "content/about.md")
                .content()
                .contains("Roq");
        codestartTest.assertThatGeneratedFile(language, "public/images/favicon.ico")
                .exists();
    }

    @Test
    void buildAll() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
