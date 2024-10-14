package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterSimpleTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "simple-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("simple-site"));

    @Test
    public void testPage() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Some page - Simple Site"))
                .body("html.body.article.h1", equalTo("Some page"))
                .body("html.body.article.p", equalTo("We can also use data"));
    }

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Simple Site"))
                .body("html.body.div.h1[0]", containsString("New Post"))
                .body("html.body.div.h1[1]", containsString("Some Post"));
    }

    @Test
    public void testStatic() {
        RestAssured.when().get("/static/assets/images/iamroq.png").then().statusCode(200).log().ifValidationFails();
    }

}
