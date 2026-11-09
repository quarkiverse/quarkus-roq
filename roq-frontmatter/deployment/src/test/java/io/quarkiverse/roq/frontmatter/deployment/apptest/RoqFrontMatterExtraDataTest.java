package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterExtraDataBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

/**
 * Site: {@code basic-site} (resource) + an extra test page added to the archive.
 * <p>
 * Features tested: plugins contributing extra data to pages via {@link RoqFrontMatterExtraDataBuildItem},
 * extra data appearing in {@code page.data}, user FM keys taking precedence over extra data.
 */
@DisplayName("Roq FrontMatter - Extra data build item")
public class RoqFrontMatterExtraDataTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .addBuildChainCustomizer(buildChainBuilder -> {
                buildChainBuilder.addBuildStep(new BuildStep() {

                    @Override
                    public void execute(BuildContext context) {
                        // Extra data for the custom page: "plugin-info" is new, should appear in rendered output
                        context.produce(new RoqFrontMatterExtraDataBuildItem(
                                "pages/extra-data-test.html",
                                new JsonObject().put("plugin-info", "contributed by plugin")));
                        // Extra data for some-page: "some-text" already exists in FM, should NOT be overwritten
                        context.produce(new RoqFrontMatterExtraDataBuildItem(
                                "pages/some-page.html",
                                new JsonObject().put("some-text", "should not override FM")));
                    }
                }).produces(RoqFrontMatterExtraDataBuildItem.class).build();
            })
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site")
                    .addAsResource(new StringAsset("""
                            ---
                            layout: page
                            title: Extra Data Test
                            link: /page/extra-data-test
                            ---
                            <span class="extra">{page.data.plugin-info}</span>
                            """), "basic-site/content/pages/extra-data-test.html"));

    @Test
    @DisplayName("Extra data appears in page.data")
    public void testExtraDataAvailable() {
        RestAssured.when().get("/page/extra-data-test").then().statusCode(200).log().ifValidationFails()
                .body(containsString("contributed by plugin"));
    }

    @Test
    @DisplayName("User FM takes precedence over extra data")
    public void testUserFmWins() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.p", equalTo("We can also use data"));
    }
}
