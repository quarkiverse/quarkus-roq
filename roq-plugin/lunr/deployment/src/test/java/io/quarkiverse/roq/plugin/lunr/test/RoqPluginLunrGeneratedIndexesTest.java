package io.quarkiverse.roq.plugin.lunr.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

/**
 * Site: {@code lunr-site} (resource) without its index files, plus pages with invalid index names and a recipe
 * declaring {@code search-index-name} (a collection document is not an index file).
 * <p>
 * Features tested: the build generates one index per name in use ({@code default} at {@code /search-index.json},
 * the others at {@code /search-index/<name>.json}), the generated indexes are known to the search script and the
 * triggers, are selected for static site generation, and invalid or reserved names are skipped with a warning.
 */
@DisplayName("Roq Plugin Lunr - generated indexes")
public class RoqPluginLunrGeneratedIndexesTest {

    private static final String SELECTED_PATHS = "/selected-paths.txt";

    private static String page(String title, String indexes) {
        return "---\ntitle: " + title + "\nlayout: default\nsearch-indexes: " + indexes + "\n---\n<p>" + title + "</p>\n";
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> {
                jar.addAsResource("application.properties")
                        .addAsResource("lunr-site")
                        .addAsResource(new StringAsset(page("Bad Index Name Page", "bad.name")),
                                "lunr-site/content/pages/bad-index-name.html")
                        .addAsResource(new StringAsset(page("Reserved Index Name Page", "all")),
                                "lunr-site/content/pages/reserved-index-name.html")
                        .addAsResource(new StringAsset(
                                "---\ntitle: Named Recipe\nsearch-index-name: recipes\n---\n<p>Named recipe</p>\n"),
                                "lunr-site/content/recipes/named.html");
                jar.delete("/lunr-site/content/search-index.json");
                jar.delete("/lunr-site/content/recipes-search-index.json");
            })
            // expose the paths selected for static site generation (Roq Generator input)
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new BuildStep() {
                @Override
                public void execute(BuildContext context) {
                    final String paths = context.consumeMulti(SelectedPathBuildItem.class).stream()
                            .map(SelectedPathBuildItem::path)
                            .sorted()
                            .collect(Collectors.joining("\n"));
                    context.produce(new GeneratedStaticResourceBuildItem(SELECTED_PATHS,
                            paths.getBytes(StandardCharsets.UTF_8)));
                }
            }).consumes(SelectedPathBuildItem.class).produces(GeneratedStaticResourceBuildItem.class).build())
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> {
                assertThat(records).as("invalid name warning")
                        .anyMatch(r -> logText(r).contains("Invalid search index name") && logText(r).contains("bad.name")
                                && logText(r).contains("bad-index-name.html"));
                assertThat(records).as("reserved name warning")
                        .anyMatch(r -> logText(r).contains("Invalid search index name")
                                && logText(r).contains("reserved-index-name.html"));
            });

    private static String logText(LogRecord record) {
        return record.getMessage() + " " + (record.getParameters() == null ? "" : List.of(record.getParameters()));
    }

    @Test
    @DisplayName("A named index is generated at /search-index/<name>.json with the pages of that index")
    public void testNamedIndexGenerated() {
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(200).log().ifValidationFails()
                .contentType(containsString("json"))
                .body(containsString("\"title\":\"Pancakes Recipe\""))
                .body(containsString("\"title\":\"Named Recipe\""))
                .body(containsString("\"title\":\"Soup Recipe\""))
                .body(not(containsString("\"title\":\"Indexed Page\"")))
                .body(not(containsString("\"title\":\"Secret Recipe\"")));
    }

    @Test
    @DisplayName("The default index is generated at /search-index.json with the pages without search-indexes")
    public void testDefaultIndexGenerated() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .contentType(containsString("json"))
                .body(containsString("\"title\":\"Indexed Page\""))
                .body(containsString("\"title\":\"Soup Recipe\""))
                .body(not(containsString("\"title\":\"Pancakes Recipe\"")))
                .body(not(containsString("\"title\":\"Search False Page\"")));
    }

    @Test
    @DisplayName("A generated index renders the same JSON documents as an index file")
    public void testGeneratedIndexDocuments() {
        final JsonObject index = new JsonObject(RestAssured.when().get("/search-index.json").then().statusCode(200)
                .extract().asString());
        assertThat(index.getMap()).isNotEmpty();
        for (String id : index.fieldNames()) {
            final JsonObject doc = index.getJsonObject(id);
            assertThat(doc.getString("title")).as("title of %s", id).isNotBlank();
            assertThat(doc.getString("url")).as("url of %s", id).startsWith("https://mywebsite.com/");
            assertThat(doc.containsKey("content")).as("content of %s", id).isTrue();
            assertThat(doc.getDouble("boost")).as("boost of %s", id).isPositive();
        }
    }

    @Test
    @DisplayName("Generated indexes are not indexed themselves")
    public void testGeneratedIndexesNotIndexed() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("search-index")));
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("search-index")));
    }

    @Test
    @DisplayName("search-script passes the generated indexes to setupSearch")
    public void testSearchScriptIndexMap() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "indexes: {\"default\":\"/search-index.json\",\"recipes\":\"/search-index/recipes.json\"}"));
    }

    @Test
    @DisplayName("all expands to the generated indexes")
    public void testAllTrigger() {
        RestAssured.when().get("/triggers/all/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-search-indexes=\"default recipes\""));
    }

    @Test
    @DisplayName("Invalid and reserved names in search-indexes get no index")
    public void testInvalidNamesNotGenerated() {
        RestAssured.when().get("/search-index/all.json").then().statusCode(404);
        RestAssured.when().get("/search-index/bad.name.json").then().statusCode(404);
    }

    @Test
    @DisplayName("Generated indexes are selected for static site generation")
    public void testGeneratedIndexesSelectedForGeneration() {
        final List<String> selected = List.of(RestAssured.when().get(SELECTED_PATHS).then().statusCode(200)
                .log().ifValidationFails().extract().asString().split("\n"));
        assertThat(selected).contains("/search-index.json", "/search-index/recipes.json");
    }
}
