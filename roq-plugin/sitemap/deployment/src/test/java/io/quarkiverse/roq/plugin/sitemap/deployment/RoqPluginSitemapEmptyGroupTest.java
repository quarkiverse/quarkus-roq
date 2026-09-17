package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.plugin.sitemap.runtime.RoqSitemapKeys;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code sitemap-html-site} (resource), with a build step marking every loose page {@code sitemap: false}
 * so that only the {@code posts} collection stays visible.
 * <p>
 * Features tested: {@code fm/sitemap.html} drops the {@code Pages} group entirely when no page is visible,
 * instead of rendering an empty heading and list.
 */
public class RoqPluginSitemapEmptyGroupTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "sitemap-html-site")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new BuildStep() {

                @Override
                public void execute(BuildContext context) {
                    context.produce(new RoqFrontMatterDataModificationBuildItem(source -> {
                        if (source.isPage() && source.collection() == null) {
                            return source.fm().copy().put(RoqSitemapKeys.SITEMAP, false);
                        }
                        return source.fm();
                    }));
                }
            }).produces(RoqFrontMatterDataModificationBuildItem.class).build())
            .withApplicationRoot((jar) -> jar
                    .addAsResource("sitemap-html-site"));

    @Test
    void omitsThePagesGroupWhenNoPageIsVisible() {
        Document doc = Jsoup.parse(RestAssured.when().get("/sitemap/").then().statusCode(200).log().ifValidationFails()
                .extract().asString());

        assertThat(doc.select("nav.roq-sitemap section.roq-sitemap-group h2").eachText())
                .as("only the collection group is left")
                .containsExactly("Posts");
        assertThat(doc.select("section.roq-sitemap-pages"))
                .as("no empty pages group")
                .isEmpty();
    }
}
