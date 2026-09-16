package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

/**
 * Site: {@code sitemap-html-site} (resource)
 * <p>
 * Config: {@code secrets} collection hidden, plus a build step publishing a derived collection over the
 * {@code posts} documents (what the tagging plugin does for tag pages).
 * <p>
 * Features tested: the HTML sitemap fragment {@code fm/sitemap.html} lists loose pages first, then groups
 * documents by collection, skips hidden and derived collections, honours the shared {@code sitemap:} front matter flag, and
 * leaves out the page that includes it. Also checks that shipping {@code fm/sitemap.html} next to
 * {@code fm/sitemap.xml} does not make Qute's suffix resolution mix up the two includes.
 */
public class RoqPluginSitemapHtmlTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "sitemap-html-site")
            .overrideConfigKey("site.collections.secrets.hidden", "true")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new BuildStep() {

                @Override
                public void execute(BuildContext context) {
                    // Derived collections reference existing documents instead of creating pages.
                    final List<String> ids = context.consumeMulti(RoqFrontMatterPublishDocumentPageBuildItem.class)
                            .stream()
                            .filter(d -> "posts".equals(d.collection().id()))
                            .map(d -> d.source().id())
                            .toList();
                    final ConfiguredCollection derived = new ConfiguredCollection("posts/derived", true, false, false,
                            null, null, Optional.empty());
                    context.produce(new RoqFrontMatterPublishDerivedCollectionBuildItem(derived, ids, new JsonObject()));
                }
            }).consumes(RoqFrontMatterPublishDocumentPageBuildItem.class)
                    .produces(RoqFrontMatterPublishDerivedCollectionBuildItem.class).build())
            .withApplicationRoot((jar) -> jar
                    .addAsResource("sitemap-html-site"));

    private static Document htmlSitemap() {
        return Jsoup.parse(RestAssured.when().get("/sitemap/").then().statusCode(200).log().ifValidationFails()
                .extract().asString());
    }

    @Test
    void groupsDocumentsByCollectionAndListsPages() {
        Document doc = htmlSitemap();

        assertThat(doc.select("nav.roq-sitemap section.roq-sitemap-group h2").eachText())
                .as("the pages group, then one group per visible collection")
                .containsExactly("Pages", "Posts");
        assertThat(doc.select("section.roq-sitemap-group:has(h2:contains(Posts)) li a").eachText())
                .as("collection documents, in the collection's natural order (newest first)")
                .containsExactly("Second Post", "First Post");
        assertThat(doc.select("section.roq-sitemap-group:has(h2:contains(Pages)) li a").eachAttr("href"))
                .as("loose pages")
                .containsExactly("/", "/about/");
    }

    @Test
    void marksEachGroupWithItsOwnStylingHook() {
        Document doc = htmlSitemap();

        assertThat(doc.select("section.roq-sitemap-pages")).as("the pages group is addressable on its own").hasSize(1);
        assertThat(doc.select("section.roq-sitemap-collection").eachAttr("data-collection"))
                .as("each collection group carries its collection id")
                .containsExactly("posts");
        assertThat(doc.select("section.roq-sitemap-pages.roq-sitemap-collection"))
                .as("the two modifiers are mutually exclusive")
                .isEmpty();
    }

    @Test
    void honoursTheSitemapFlagAndSkipsTheIncludingPage() {
        assertThat(htmlSitemap().select("nav.roq-sitemap li a").eachAttr("href"))
                .as("sitemap: false pages excluded, and the sitemap page does not link to itself")
                .doesNotContain("/posts/excluded/", "/hidden-page/", "/sitemap/");
    }

    @Test
    void skipsHiddenAndDerivedCollections() {
        assertThat(htmlSitemap().select("nav.roq-sitemap section.roq-sitemap-group h2").eachText())
                .as("hidden 'secrets' and derived 'posts/derived' get no group of their own")
                .doesNotContain("Secrets", "Posts/derived");
    }

    @Test
    void xmlSitemapIsNotShadowedByTheHtmlOne() {
        String xml = RestAssured.when().get("/sitemap.xml").then().statusCode(200).log().ifValidationFails()
                .extract().asString();

        assertThat(xml)
                .as("fm/sitemap.xml still resolves to the XML template")
                .contains("<urlset").doesNotContain("roq-sitemap");
        assertThat(xml)
                .as("the HTML sitemap page is itself indexable")
                .contains("<loc>/sitemap/</loc>");
    }
}
