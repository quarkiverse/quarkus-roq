package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterApiModificationTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "simple-site")
            .addBuildChainCustomizer(buildChainBuilder -> {
                buildChainBuilder.addBuildStep(new BuildStep() {

                    @Override
                    public void execute(BuildContext context) {
                        context.produce(new RoqFrontMatterDataModificationBuildItem((sourcePath, data) -> {
                            if (sourcePath.equals("pages/some-page.html")) {
                                final JsonObject newData = data.copy();
                                newData.put("some-text", "modified text");
                                newData.put("link", "/somewhere-else");
                                return newData;
                            }
                            return data;
                        }));
                    }
                }).produces(RoqFrontMatterDataModificationBuildItem.class).build();

            })
            .withApplicationRoot((jar) -> jar
                    .addAsResource("simple-site"));

    @Test
    public void testPage() {
        RestAssured.when().get("/somewhere-else").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.p", equalTo("modified text"));
    }

}
