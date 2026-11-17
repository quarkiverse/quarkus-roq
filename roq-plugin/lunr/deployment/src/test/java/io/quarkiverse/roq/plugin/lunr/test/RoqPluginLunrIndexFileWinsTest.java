package io.quarkiverse.roq.plugin.lunr.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource) with its index files replaced by unnamed ones: {@code content/search-index.json}
 * moved to another URL with {@code link}, and {@code content/search-index/recipes.json} at the URL of the
 * {@code recipes} index.
 * <p>
 * Features tested: a content file at an index content path does not clash with the generated template (regression:
 * duplicate Qute templates) and the index is generated at its URL; a page serving an index URL takes the place of the
 * generated index without a duplicate path, and is reported when it does not declare the name.
 */
@DisplayName("Roq Plugin Lunr - index files win over generated indexes")
public class RoqPluginLunrIndexFileWinsTest {

    private static final String UNNAMED_INDEX = "{#include fm/search-index.json /}\n";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> {
                jar.addAsResource("application.properties")
                        .addAsResource("lunr-site");
                jar.delete("/lunr-site/content/search-index.json");
                jar.delete("/lunr-site/content/recipes-search-index.json");
                jar.addAsResource(new StringAsset("---\nlink: /moved-index.json\n---\n" + UNNAMED_INDEX),
                        "lunr-site/content/search-index.json")
                        .addAsResource(new StringAsset(UNNAMED_INDEX), "lunr-site/content/search-index/recipes.json");
            })
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records).as("named index taken by an unnamed file")
                    .anyMatch(r -> logText(r).contains("is not generated") && logText(r).contains("recipes, ")
                            && logText(r).contains("search-index/recipes.json")
                            && logText(r).contains("search-index-name")));

    private static String logText(LogRecord record) {
        return record.getMessage() + " " + (record.getParameters() == null ? "" : List.of(record.getParameters()));
    }

    @Test
    @DisplayName("A default index file moved with link is served and the default index is still generated")
    public void testMovedDefaultIndexFileAndGeneratedDefault() {
        RestAssured.when().get("/moved-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"default\":\"/search-index.json\""));
    }

    @Test
    @DisplayName("An unnamed file at a named index URL is served instead of the generated index")
    public void testFileAtNamedIndexPathWins() {
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
    }
}
