package io.quarkiverse.roq.plugin.aliases.deployment;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code alias-valid-site} (resource)
 * <p>
 * Features tested: aliases that do not conflict with any page or static file build and redirect.
 */
@DisplayName("Roq Aliases - Non-conflicting aliases redirect")
public class RoqAliasesRedirectTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "alias-valid-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("alias-valid-site"));

    @Test
    @DisplayName("The page itself is served")
    public void testPageIsServed() {
        RestAssured.when().get("/about/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<h1>About</h1>"));
    }

    @Test
    @DisplayName("An alias from 'aliases' redirects with and without trailing slash")
    public void testAliasRedirects() {
        RestAssured.when().get("/old-about/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"))
                .body(containsString("/about/"));
        RestAssured.when().get("/old-about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    @DisplayName("A nested alias without leading slash redirects")
    public void testNestedAliasRedirects() {
        RestAssured.when().get("/legacy/about/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    @DisplayName("An alias from 'redirect_from' redirects")
    public void testRedirectFromRedirects() {
        RestAssured.when().get("/very-old-about/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }
}
