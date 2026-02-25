package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqAvatarTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("avatar/application.properties", "application.properties")
                    .addAsResource("avatar/site"));

    @Test
    public void testFallbackAvatarIsRenderedAndPublished() {
        RestAssured.when().get("/posts/avatar-fallback-test").then().statusCode(200).log().ifValidationFails()
                .body("html.body.img.@src", equalTo("/images/roq-default/default-avatar.svg"))
                .body("html.body.img.@alt", equalTo("author-jdoe"))
                .body(containsString("Avatar fallback test"));

        RestAssured.when().get("/images/roq-default/default-avatar.svg").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<svg"));
    }
}
