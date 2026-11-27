package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqThemeDefaultLanguageSwitcherTest {

    private static String get(String path) {
        return RestAssured.when().get(path).then().statusCode(200).log().ifValidationFails().extract().asString();
    }

    @Test
    public void testSwitcherOnDefaultVariant() {
        final String body = get("/posts/bilingual-post/");
        assertThat(body).contains("language-switcher");
        assertThat(body).contains("href=\"/fr/posts/bilingual-post/\"");
        assertThat(body).contains("hreflang=\"fr\"");
    }

    @Test
    public void testSwitcherOnTranslation() {
        final String body = get("/fr/posts/bilingual-post/");
        assertThat(body).contains("<html lang=\"fr\"");
        assertThat(body).contains("language-switcher");
        assertThat(body).contains("href=\"/posts/bilingual-post/\"");
        assertThat(body).contains("hreflang=\"en\"");
    }

    @Test
    public void testNoSwitcherWithoutTranslations() {
        assertThat(get("/posts/post-without-avatar/")).doesNotContain("language-switcher");
    }
}
