package io.quarkiverse.roq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class RoqCodestartResumeTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(QuarkusCodestartCatalog.Language.JAVA, QuarkusCodestartCatalog.Language.KOTLIN,
                    QuarkusCodestartCatalog.Language.SCALA)
            .setupStandaloneExtensionTest("io.quarkiverse.roq:quarkus-roq-theme-resume")
            .build();

    @ParameterizedTest
    @EnumSource(QuarkusCodestartCatalog.Language.class)
    void testContent(QuarkusCodestartCatalog.Language language) throws Throwable {
        codestartTest.assertThatGeneratedFile(language, "content/index.html")
                .content().contains("layout: resume");
        codestartTest.assertThatGeneratedFile(language, "data/bio.yml")
                .content()
                .contains("Mathematician and Writer");
        codestartTest.assertThatGeneratedFile(language, "data/profile.yml")
                .content()
                .contains("Ada");
    }

    @Test
    void buildAll() throws Throwable {
        codestartTest.buildAllProjects();
    }

}