package io.quarkiverse.roq;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

@QuarkusTest
public class RoqThemeLinktreeTest {

    private static final RestAssuredConfig TIMEOUT_CONFIG = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.socket.timeout", (int) TimeUnit.SECONDS.toMillis(60))
                    .setParam("http.connection.timeout", (int) TimeUnit.SECONDS.toMillis(60)));

    @Test
    void testLinktreeLayout() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("@adalovelace");
        assertThat(body).contains("Computational Pioneer");
        assertThat(body).contains("ada.png");
        assertThat(body).contains("ph-fill ph-github-logo");
        assertThat(body).contains("https://example.com");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("A test link");
        assertThat(body).contains("ph-tree-structure");
    }

    @Test
    void testLinktreesLayout() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/trees")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("All Trees");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("Test Links");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("downloadQR");
    }

    @Test
    void testTreeCollectionPage() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/trees/test-links/")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("Test Links");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("https://example.com");
    }
}
