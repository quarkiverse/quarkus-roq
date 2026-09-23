package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code sitemap-html-site} (resource)
 * <p>
 * Config: {@code quarkus.roq.sitemap.last-modified=none}
 * <p>
 * Features tested: without a last modified source, {@code <lastmod>} falls back to the document date.
 */
public class RoqPluginSitemapLastModifiedNoneTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "sitemap-html-site")
            .overrideConfigKey("quarkus.roq.sitemap.last-modified", "none")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("sitemap-html-site"));

    @Test
    void usesTheDocumentDate() {
        Document doc = Jsoup.parse(RestAssured.when().get("/sitemap.xml").then().statusCode(200).log().ifValidationFails()
                .extract().asString(), Parser.xmlParser());

        assertThat(doc.select("url:has(loc:containsOwn(second)) lastmod").text())
                .as("the date from the file name")
                .startsWith("2026-01-02");
    }
}
