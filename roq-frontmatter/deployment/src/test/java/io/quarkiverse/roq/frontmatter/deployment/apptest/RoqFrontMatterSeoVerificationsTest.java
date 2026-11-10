package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: seo-verifications-site (index declaring a single webmasterVerifications key and a single facebook key)
 * <p>
 * Features tested: the {@code {#seo}} tag must render when only a subset of the optional
 * {@code webmasterVerifications} / {@code facebook} keys is declared.
 */
@DisplayName("Roq FrontMatter - SEO tags with a subset of optional keys")
public class RoqFrontMatterSeoVerificationsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "seo-verifications-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("seo-verifications-site"));

    @Test
    @DisplayName("Index page renders only the declared verification and facebook meta tags")
    public void testSubsetOfKeys() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<meta name=\"google-site-verification\" content=\"google-verification-code\">"))
                .body(containsString("<meta property=\"fb:app_id\" content=\"facebook-app-id\" />"))
                .body(not(containsString("msvalidate.01")))
                .body(not(containsString("yandex-verification")))
                .body(not(containsString("fb:admins")))
                .body(not(containsString("article:publisher")));
    }
}
