package io.quarkiverse.roq.plugin.lunr.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource)
 * <p>
 * Features tested: the {@code indexes} parameter of the search trigger tags renders the resolved index names as
 * {@code data-search-indexes}, and {@code search-script} passes the index name to URL map to {@code setupSearch}.
 */
@DisplayName("Roq Plugin Lunr - trigger indexes")
public class RoqPluginLunrTriggerIndexesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site"))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records)
                    .as("warning for the unknown index name used by a trigger")
                    .anyMatch(record -> logText(record).contains("Unknown search index")
                            && logText(record).contains("nope")));

    private static String logText(LogRecord record) {
        return record.getMessage() + " " + (record.getParameters() == null ? "" : List.of(record.getParameters()));
    }

    @Test
    @DisplayName("A single index name is rendered on the trigger")
    public void testSingleIndex() {
        RestAssured.when().get("/triggers/single/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("aria-label=\"Search...\" data-search-indexes=\"recipes\">"));
    }

    @Test
    @DisplayName("A list of index names keeps its order")
    public void testListOfIndexes() {
        RestAssured.when().get("/triggers/list/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-search-indexes=\"recipes default\""));
    }

    @Test
    @DisplayName("all expands to every index")
    public void testAllIndexes() {
        RestAssured.when().get("/triggers/all/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-search-indexes=\"default recipes\""));
    }

    @Test
    @DisplayName("Unknown index names are dropped")
    public void testUnknownIndexDropped() {
        RestAssured.when().get("/triggers/unknown/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-search-indexes=\"recipes\""));
    }

    @Test
    @DisplayName("Only unknown index names render the trigger without the attribute")
    public void testOnlyUnknownIndexes() {
        RestAssured.when().get("/triggers/only-unknown/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("class=\"search-trigger-input\""))
                .body(not(containsString("data-search-indexes")));
    }

    @Test
    @DisplayName("Index names can be passed from page front matter")
    public void testIndexesFromFrontMatter() {
        RestAssured.when().get("/triggers/from-fm/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-search-indexes=\"recipes\""));
    }

    @Test
    @DisplayName("A missing front matter value renders the trigger without the attribute")
    public void testMissingFrontMatterValue() {
        RestAssured.when().get("/triggers/from-fm-missing/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("class=\"search-trigger-input\""))
                .body(not(containsString("data-search-indexes")));
    }

    @Test
    @DisplayName("The icon search button accepts indexes too")
    public void testSearchButtonIndexes() {
        RestAssured.when().get("/triggers/button/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "<button type=\"button\" id=\"search-button\" class=\"search-button\" aria-label=\"Search\" data-search-indexes=\"recipes\">"));
    }

    @Test
    @DisplayName("search-script passes the index map, including default, to setupSearch")
    public void testSearchScriptIndexMap() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("url: '")))
                .body(containsString(
                        "indexes: {\"default\":\"/search-index.json\",\"recipes\":\"/recipes-search-index.json\"}"));
    }
}
