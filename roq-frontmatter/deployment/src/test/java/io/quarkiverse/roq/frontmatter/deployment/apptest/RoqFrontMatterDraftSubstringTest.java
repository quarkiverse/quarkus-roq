package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Draft substring path detection (drafts disabled)")
public class RoqFrontMatterDraftSubstringTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-substring-site")
            .withApplicationRoot((jar) -> jar.addAsResource("draft-substring-site"));

    @Test
    @DisplayName("Post in drafts/ directory is hidden")
    public void testRealDraftsDirPostHidden() {
        RestAssured.when().get("/posts/auto-drafts-dir-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Post in mydrafts/ directory is visible (substring must not match)")
    public void testMydraftsSubstringPostVisible() {
        RestAssured.when().get("/posts/false-positive-post").then().statusCode(200).log().ifValidationFails();
    }
}
