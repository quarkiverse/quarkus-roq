package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterUnpublishedFileBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code files-site} (resource)
 * <p>
 * Config: defaults + custom BuildStep producing RoqFrontMatterUnpublishedFileBuildItem for
 * {@code images/cover.png}
 * <p>
 * Features tested: an extension can keep a scanned static file out of the published site (it is neither
 * served nor selected for generation) while pages still resolve it as an image.
 */
@DisplayName("Roq FrontMatter - Unpublished static files")
public class RoqFrontMatterUnpublishedFileTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "files-site")
            .addBuildChainCustomizer(builder -> builder
                    .addBuildStep(new BuildStep() {
                        @Override
                        public void execute(BuildContext context) {
                            context.produce(new RoqFrontMatterUnpublishedFileBuildItem("images/cover.png"));
                        }
                    })
                    .produces(RoqFrontMatterUnpublishedFileBuildItem.class)
                    .build())
            .withApplicationRoot((jar) -> jar.addAsResource("files-site"));

    @Test
    @DisplayName("Unpublished file is not served")
    void unpublishedFileIsNotServed() {
        RestAssured.when().get("/images/cover.png").then().statusCode(404);
    }

    @Test
    @DisplayName("Other static files are still served")
    void otherFilesStillServed() {
        RestAssured.when().get("/images/logo.png").then().statusCode(200);
    }

    @Test
    @DisplayName("Pages still resolve the unpublished image")
    void pageStillResolvesTheImage() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'site-image-url' }.text()",
                        containsString("images/cover.png"));
    }
}
