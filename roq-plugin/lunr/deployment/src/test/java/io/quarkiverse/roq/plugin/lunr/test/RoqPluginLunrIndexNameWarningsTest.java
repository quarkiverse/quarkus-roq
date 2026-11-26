package io.quarkiverse.roq.plugin.lunr.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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
 * Site: {@code lunr-site} (resource) plus extra index files with naming problems.
 * <p>
 * Features tested: duplicate, invalid and reserved {@code search-index-name} values are logged and left out of the
 * index map passed to {@code setupSearch}.
 */
@DisplayName("Roq Plugin Lunr - index name warnings")
public class RoqPluginLunrIndexNameWarningsTest {

    private static String indexFile(String name) {
        return "---\nsearch-index-name: " + name + "\n---\n{#include fm/search-index.json /}\n";
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site")
                    .addAsResource(new StringAsset(indexFile("recipes")), "lunr-site/content/zz-recipes-copy.json")
                    .addAsResource(new StringAsset(indexFile("\"bad name\"")), "lunr-site/content/bad-name.json")
                    .addAsResource(new StringAsset(indexFile("all")), "lunr-site/content/all-index.json"))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> {
                assertThat(records).as("duplicate name warning")
                        .anyMatch(r -> logText(r).contains("Duplicate search index name") && logText(r).contains("recipes"));
                assertThat(records).as("invalid name warning")
                        .anyMatch(r -> logText(r).contains("Invalid search index name") && logText(r).contains("bad name"));
                assertThat(records).as("reserved name warning")
                        .anyMatch(
                                r -> logText(r).contains("Invalid search index name") && logText(r).contains("all-index.json"));
            });

    private static String logText(LogRecord record) {
        return record.getMessage() + " " + (record.getParameters() == null ? "" : List.of(record.getParameters()));
    }

    @Test
    @DisplayName("The first index file wins for a duplicate name, invalid and reserved names are left out")
    public void testIndexMapSkipsProblematicNames() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "indexes: {\"default\":\"/search-index.json\",\"recipes\":\"/recipes-search-index.json\"}"))
                .body(not(containsString("zz-recipes-copy")))
                .body(not(containsString("bad-name")))
                .body(not(containsString("all-index")));
    }
}
