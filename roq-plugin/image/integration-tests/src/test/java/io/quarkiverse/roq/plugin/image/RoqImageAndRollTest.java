package io.quarkiverse.roq.plugin.image;

import static io.quarkiverse.roq.plugin.image.Pages.generatedPaths;
import static io.quarkiverse.roq.plugin.image.Pages.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkiverse.roq.testing.RoqLinks;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

/**
 * The generated site contains the converted variants and not the originals they replace.
 */
@QuarkusTest
@RoqAndRoll
@TestProfile(RoqImageAndRollTest.Profile.class)
public class RoqImageAndRollTest {

    @Test
    public void testGeneratedImagesWrittenToOutput() {
        List<String> generated = generatedPaths(get("/expressions/"));
        assertThat(generated).isNotEmpty();
        for (String path : generated) {
            assertThat(RoqLinks.outputDir().resolve(path.substring(1))).exists();
        }
    }

    @Test
    public void testOriginalsNotPublished() {
        // a page attachment converted through an expression, and a public file converted through a literal
        RestAssured.get("/expressions/hero-landscape.jpg").then().statusCode(404);
        RestAssured.get("/images/hero-landscape.jpg").then().statusCode(404);
        RestAssured.get("/expressions/notes.txt").then().statusCode(200);
        assertThat(RoqLinks.outputDir().resolve("expressions/hero-landscape.jpg")).doesNotExist();
        assertThat(RoqLinks.outputDir().resolve("images/hero-landscape.jpg")).doesNotExist();
    }

    @Test
    public void testUnconvertedOriginalStaysPublished() {
        RestAssured.get("/vector/logo.svg").then().statusCode(200);
        assertThat(RoqLinks.outputDir().resolve("vector/logo.svg")).exists();
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "roq-and-roll";
        }
    }
}
