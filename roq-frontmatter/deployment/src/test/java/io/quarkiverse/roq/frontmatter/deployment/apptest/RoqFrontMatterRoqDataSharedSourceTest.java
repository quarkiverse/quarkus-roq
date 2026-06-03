package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Two collections sharing the same data source")
public class RoqFrontMatterRoqDataSharedSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("site.collections.events.layout", "page-event")
            .overrideConfigKey("site.collections.events.from-data.id-key", "id")
            .overrideConfigKey("site.collections.highlights.layout", "page-highlight")
            .overrideConfigKey("site.collections.highlights.from-data.id-key", "id")
            .overrideConfigKey("site.collections.highlights.from-data.name", "events")
            .overrideConfigKey("quarkus.roq.resource-dir", "data-generated-page-dir")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("data-generated-page-dir"));

    @Test
    @DisplayName("Events collection renders with event layout")
    public void testEventsCollection() {
        RestAssured.when().get("/events/hello-lads/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Hello lads !"))
                .body("html.body.h2", equalTo("First event"))
                .body("html.body.p", equalTo("This is the first event"));
    }

    @Test
    @DisplayName("Highlights collection renders with highlight layout")
    public void testHighlightsCollection() {
        RestAssured.when().get("/highlights/hello-lads/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("HIGHLIGHT: Hello lads !"))
                .body("html.body.p", equalTo("This is the first event"));
    }

    @Test
    @DisplayName("Both collections have second item")
    public void testSecondItem() {
        RestAssured.when().get("/events/roq-and-roll/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Roq and roll"));
        RestAssured.when().get("/highlights/roq-and-roll/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("HIGHLIGHT: Roq and roll"));
    }
}
