package io.quarkiverse.roq.plugin.image;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

/**
 * With {@code quarkus.roq.plugin.image.publish-originals=true} the converted originals stay published.
 */
@QuarkusTest
@TestProfile(RoqImagePublishOriginalsTest.Profile.class)
public class RoqImagePublishOriginalsTest {

    @Test
    public void testConvertedOriginalsServed() {
        RestAssured.get("/expressions/hero-landscape.jpg").then().statusCode(200);
        RestAssured.get("/images/hero-landscape.jpg").then().statusCode(200);
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.roq.plugin.image.publish-originals", "true");
        }
    }
}
