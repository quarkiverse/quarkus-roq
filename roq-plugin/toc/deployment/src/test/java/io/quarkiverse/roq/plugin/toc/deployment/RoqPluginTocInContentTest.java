package io.quarkiverse.roq.plugin.toc.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code toc-in-content-site} (resource)
 * <p>
 * Features tested: a page whose own content contains {@code {page.tocHtml}} still renders, the TOC in the
 * layout and the one in the content both list the page headings, and no recursion warning is logged.
 * Building the TOC renders the page content, so the expression inside the content re-enters the extension;
 * the guard in {@code RoqPluginTocTemplateExtension#toc} keeps that nested render finite.
 */
public class RoqPluginTocInContentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "toc-in-content-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("toc-in-content-site"))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records)
                    .as("warnings logged while rendering")
                    .noneMatch(record -> String.valueOf(record.getMessage()).contains("Recursive call")));

    @Test
    void tocInsidePageContentRendersWithoutRecursion() {
        String html = RestAssured.when().get("/pages/inline-toc/").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        Document doc = Jsoup.parse(html);

        assertThat(doc.select("#layout-toc nav.roq-toc a").eachAttr("href"))
                .as("TOC rendered by the layout")
                .containsExactly("#first", "#second");
        assertThat(doc.select("#inline-toc nav.roq-toc a").eachAttr("href"))
                .as("TOC rendered inside the page content")
                .containsExactly("#first", "#second");
    }
}
