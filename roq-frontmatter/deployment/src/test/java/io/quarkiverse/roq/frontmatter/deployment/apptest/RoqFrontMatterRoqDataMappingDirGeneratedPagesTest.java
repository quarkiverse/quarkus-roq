package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Roq data directory generated pages")
public class RoqFrontMatterRoqDataMappingDirGeneratedPagesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()

            .overrideConfigKey("site.collections.incidents.layout", "page-event")
            .overrideConfigKey("site.collections.incidents.from-data.id-key", "id")
            .overrideConfigKey("quarkus.roq.resource-dir", "data-generated-page-dir")
            .withApplicationRoot((jar) -> jar
                    .addClasses(Incidents.class, Incidents.Incident.class)
                    .addAsResource("data-generated-page-dir"));

    @Test
    @DisplayName("First item from directory rendered")
    public void testFirstEvent() {
        RestAssured.when().get("/incidents/hello-lads/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Hello lads !"))
                .body("html.body.h2", equalTo("First event"))
                .body("html.body.p", equalTo("This is the first event"));
    }

    @Test
    @DisplayName("Second item from directory rendered")
    public void testSecondEvent() {
        RestAssured.when().get("/incidents/roq-and-roll/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Roq and roll"))
                .body("html.body.h2", equalTo("SSG FTW"))
                .body("html.body.p", equalTo("Static site generation with Roq"));
    }
}
