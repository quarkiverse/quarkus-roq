package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Draft directory config normalized (trailing slash)")
public class RoqFrontMatterDraftTrailingSlashTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-trailing-slash-site")
            .overrideConfigKey("site.draft-directory", "drafts/")
            .withApplicationRoot((jar) -> jar.addAsResource("draft-trailing-slash-site"));

    @Test
    @DisplayName("Post in drafts/ directory is hidden (trailing slash in config normalized)")
    public void testRealDraftsDirPostHiddenWithTrailingSlashConfig() {
        RestAssured.when().get("/posts/auto-draft-post").then().statusCode(404).log().ifValidationFails();
    }
}
