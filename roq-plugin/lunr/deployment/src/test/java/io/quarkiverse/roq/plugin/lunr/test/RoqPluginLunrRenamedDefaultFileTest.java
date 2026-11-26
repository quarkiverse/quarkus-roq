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
 * Site: {@code lunr-site} (resource) with {@code content/search-index.json} declaring {@code search-index-name: posts}.
 * <p>
 * Features tested: a file at {@code /search-index.json} naming another index leaves the site without a
 * {@code default} index; the build reports it instead of generating into a path conflict.
 */
@DisplayName("Roq Plugin Lunr - renamed default index file")
public class RoqPluginLunrRenamedDefaultFileTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> {
                jar.addAsResource("application.properties")
                        .addAsResource("lunr-site");
                jar.delete("/lunr-site/content/search-index.json");
                jar.addAsResource(new StringAsset("---\nsearch-index-name: posts\n---\n{#include fm/search-index.json /}\n"),
                        "lunr-site/content/search-index.json");
            })
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records).as("default index hidden by a renamed file")
                    .anyMatch(r -> logText(r).contains("is not generated") && logText(r).contains("default, ")
                            && logText(r).contains("search-index.json")));

    private static String logText(LogRecord record) {
        return record.getMessage() + " " + (record.getParameters() == null ? "" : List.of(record.getParameters()));
    }

    @Test
    @DisplayName("The renamed file keeps its URL, renders its own (empty) index and is mapped under its name")
    public void testRenamedFileServed() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("{}"));
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"posts\":\"/search-index.json\""));
    }
}
