package io.quarkiverse.roq.plugin.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqImageTest {

    @Test
    public void testPageRendersWithImages() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).log().ifValidationFails()
                .extract().asString();

        assertThat(body).contains("<picture");
        assertThat(body).contains("/static/images/generated/");
        assertThat(body).contains("alt=\"FM hero\"");
        assertThat(body).contains("alt=\"Author\"");
        assertThat(body).contains("alt=\"Absolute ref\"");
        assertThat(body).contains("alt=\"Page image\"");
    }

    @Test
    public void testGeneratedImagesAccessible() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        for (String token : body.split("[\"\\s,]+")) {
            if (token.startsWith("/static/images/generated/")) {
                RestAssured.when().get(token)
                        .then().statusCode(200);
            }
        }
    }

    @Test
    public void testHeroPresetWidths() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("350w");
        assertThat(body).contains("600w");
        assertThat(body).contains("1000w");
    }

    @Test
    public void testAvatarPixelRatio() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("1x");
        assertThat(body).contains("2x");
    }

    @Test
    public void testDirectUrlOutput() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        int start = body.indexOf("class=\"fm-direct\"");
        int end = body.indexOf("</section>", start);
        String directSection = body.substring(start, end);
        assertThat(directSection).contains("/static/images/generated/");
        assertThat(directSection).doesNotContain("<img");
        assertThat(directSection).doesNotContain("<picture");
    }

    @Test
    public void testPageLevelImage() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("alt=\"Page image\"");
        assertThat(body).contains("400w");
        assertThat(body).contains("800w");
    }
}
