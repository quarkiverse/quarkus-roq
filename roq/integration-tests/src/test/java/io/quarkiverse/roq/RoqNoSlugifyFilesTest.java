package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

@QuarkusTest
@TestProfile(RoqNoSlugifyFilesTest.NoSlugifyConfig.class)
public class RoqNoSlugifyFilesTest {

    @Test
    public void testPageDir() {
        RestAssured.when().get("/posts/2010-08-05-hello-world").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "and an images: /images/hello.png, /images/hello.foo.png and /posts/2010-08-05-hello-world/hello-page.png and  /posts/2010-08-05-hello-world/hello-page.png"));
        RestAssured.when().get("/images/hello.foo.png").then().statusCode(200).log().ifValidationFails();
    }

    public static class NoSlugifyConfig implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "no-file-slugify";
        }
    }

}
