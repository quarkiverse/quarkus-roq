package io.quarkiverse.roq;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class RoqCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(QuarkusCodestartCatalog.Language.JAVA, QuarkusCodestartCatalog.Language.KOTLIN,
                    QuarkusCodestartCatalog.Language.SCALA)
            .setupStandaloneExtensionTest("io.quarkiverse.roq:quarkus-roq")
            .build();

    @ParameterizedTest
    @EnumSource(QuarkusCodestartCatalog.Language.class)
    void testContent(QuarkusCodestartCatalog.Language language) throws Throwable {
        codestartTest.assertThatGeneratedFile(language, "content/index.html")
                .content().contains("title: Hello, world! I'm Roq");
        codestartTest.assertThatGeneratedFile(language, "src/main/resources/web/app/main.js")
                .content()
                .contains("""
                        console.log("Hello, you Roq! I am at src/main/resources/web/app/main.js")""");
        codestartTest.assertThatGeneratedFile(language, "data/authors.yml")
                .content()
                .contains("qlover@quarkuslover.io");
        codestartTest.assertThatGeneratedFile(language, "data/menu.yml")
                .content()
                .contains("fa-regular fa-newspaper");
        codestartTest.assertThatGeneratedFile(language, "static/assets/images/iamroq.png")
                .exists();
    }

}
