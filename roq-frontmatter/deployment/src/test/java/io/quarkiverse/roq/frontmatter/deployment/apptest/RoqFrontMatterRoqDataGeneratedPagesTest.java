package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code routing-site} (resource)
 * <p>
 * Config: path-prefix=/bar, root-path=/foo
 * <p>
 * Features tested: Generated page from roq-data values
 */
@DisplayName("Roq FrontMatter - Roq data generated pages")
public class RoqFrontMatterRoqDataGeneratedPagesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("site.collections.events", "true")
            .overrideConfigKey("site.collections.members", "true")
            .overrideConfigKey("site.collections.events.generate", "true")
            .overrideConfigKey("site.collections.events.layout", "page-event")
            .overrideConfigKey("site.collections.events.title-attribute-name", "id")
            .overrideConfigKey("site.collections.members.title-attribute-name", "alias")
            .overrideConfigKey("smallrye.config.mapping.validate-unknown", "false")
            .overrideConfigKey("quarkus.roq.resource-dir", "data-generated-page")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("data-generated-page"));

    @Test
    @DisplayName("First item rendered")
    public void testHtmlPost1() {
        RestAssured.when().get("/events/hello-lads/").then().statusCode(200).log().everything()
                .body("html.body.h1", equalTo("Hello lads !"))
                .body("html.body.h2", equalTo("First event"))
                .body("html.body.p", equalTo("""
                        Hey we can
                        event have
                        multiline string !
                        """));
    }

    @Test
    @DisplayName("Second item rendered")
    public void testHtmlPost2() {
        RestAssured.when().get("events/rock-and-roll/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Roq and Roll"))
                .body("html.body.h2", equalTo("SSG FTW"))
                .body("html.body.p",
                        equalTo("Of course you should do static site generation, but with qute and roq, not JSP's"));
    }

    @Test
    @DisplayName("Pages not generated when not explicitely asked")
    public void testDateFileName() {
        RestAssured.when().get("/members/the-goat").then().statusCode(404).log().ifValidationFails();
    }

}
