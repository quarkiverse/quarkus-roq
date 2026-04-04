package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

/**
 * Site: {@code basic-site} (resource)
 * <p>
 * Config: defaults + custom BuildStep producing RoqFrontMatterDataModificationBuildItem
 * <p>
 * Features tested: build-time API for modifying FrontMatter data programmatically,
 * link override, content data override.
 */
@DisplayName("Roq FrontMatter - Build-time data modification API")
public class RoqFrontMatterApiModificationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .addBuildChainCustomizer(buildChainBuilder -> {
                buildChainBuilder.addBuildStep(new BuildStep() {

                    @Override
                    public void execute(BuildContext context) {
                        context.produce(new RoqFrontMatterDataModificationBuildItem((sourceInfo) -> {
                            if (sourceInfo.relativePath().equals("pages/some-page.html")) {
                                final JsonObject newData = sourceInfo.fm().copy();
                                newData.put("some-text", "modified text");
                                newData.put("link", "/somewhere-else");
                                return newData;
                            }
                            return sourceInfo.fm();
                        }));
                    }
                }).produces(RoqFrontMatterDataModificationBuildItem.class).build();

            })
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site"));

    @Test
    @DisplayName("Modified FM data changes page link and content")
    public void testPage() {
        RestAssured.when().get("/somewhere-else").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.p", equalTo("modified text"));
    }

}
