package io.quarkiverse.roq.plugin.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

@QuarkusTest
@RoqAndRoll
@TestProfile(RoqImageAndRollTest.Profile.class)
public class RoqImageAndRollTest {

    @Test
    public void testGeneratedPageContainsImages() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).log().ifValidationFails()
                .extract().asString();

        assertThat(body).contains("<picture");
        assertThat(body).contains("/static/images/generated/");
        assertThat(body).contains("alt=\"FM hero\"");
        assertThat(body).contains("alt=\"Absolute ref\"");
    }

    @Test
    public void testGeneratedImagesServed() {
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
    public void testBuildTimeProcessedImageServed() {
        final String body = RestAssured.when().get("/image-test/")
                .then().statusCode(200).extract().asString();

        assertThat(body).contains("350w");
        assertThat(body).contains("600w");
        assertThat(body).contains("1000w");
    }

    @Test
    public void testOriginalsNotPublished() {
        RestAssured.when().get("/images/hero-landscape.jpg")
                .then().statusCode(404);
        RestAssured.when().get("/_images/hero-landscape.jpg")
                .then().statusCode(404);
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "roq-and-roll";
        }
    }
}
