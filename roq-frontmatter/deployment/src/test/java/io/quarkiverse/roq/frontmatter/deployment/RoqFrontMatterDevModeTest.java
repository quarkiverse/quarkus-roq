package io.quarkiverse.roq.frontmatter.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Test HTTP HEAD method support in dev mode.
 * In production mode, static files are generated and served by Quarkus's standard
 * static resource handler which correctly supports HEAD requests.
 * In dev mode, dynamic route handlers do not necessarily handle HEAD requests.
 */
@DisplayName("Roq FrontMatter - Dev Mode HTTP Methods")
public class RoqFrontMatterDevModeTest {

    private static final int DEV_MODE_PORT = 9382;

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site")
                    .addAsResource(
                            new org.jboss.shrinkwrap.api.asset.StringAsset(
                                    "quarkus.http.port=" + DEV_MODE_PORT + "\n" +
                                            "quarkus.http.root-path=/\n" +
                                            "quarkus.roq.resource-dir=basic-site"),
                            "application.properties"));

    @Test
    @DisplayName("GET request to index page returns 200")
    public void testGetIndexPage() {
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .get("/")
                .then()
                .statusCode(200)
                .log().ifValidationFails();
    }

    @Test
    @DisplayName("HEAD request to index page returns 200 (root is served differently than Roq pages)")
    public void testHeadIndexPage() {
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .when()
                .head("/")
                .then()
                .log().ifValidationFails()
                .statusCode(200);
    }

    @Test
    @DisplayName("GET request to dynamic page returns 200")
    public void testGetDynamicPage() {
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .get("/page/some-page/")
                .then()
                .statusCode(200)
                .log().ifValidationFails();
    }

    @Test
    @DisplayName("HEAD request to dynamic page should return 200")
    public void testHeadDynamicPage() {
        RestAssured.given()
                .port(DEV_MODE_PORT)
                .when()
                .head("/page/some-page/")
                .then()
                .log().all()
                .statusCode(200);
    }
}
