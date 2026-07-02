package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Draft substring path detection (drafts enabled)")
public class RoqFrontMatterDraftSubstringEnabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-substring-site")
            .overrideConfigKey("site.draft", "true")
            .withApplicationRoot((jar) -> jar.addAsResource("draft-substring-site"));

    @Test
    @DisplayName("Post in drafts/ directory is visible")
    public void testRealDraftsDirPostVisible() {
        RestAssured.when().get("/posts/auto-drafts-dir-post").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Post in mydrafts/ directory is not auto-draft (substring must not match)")
    public void testMydraftsSubstringPostNotAutoDraft() {
        RestAssured.when().get("/posts/false-positive-post").then().statusCode(200).log().ifValidationFails();
    }
}
