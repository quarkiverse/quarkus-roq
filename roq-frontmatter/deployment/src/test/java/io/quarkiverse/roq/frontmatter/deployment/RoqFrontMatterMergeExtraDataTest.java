package io.quarkiverse.roq.frontmatter.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterExtraDataBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoqFrontMatterMergeExtraDataTest {

    @Test
    void noExtras() {
        JsonObject pageData = new JsonObject().put("title", "Hello");
        JsonObject result = RoqFrontMatterStep5RecordProcessor.mergeExtraData(pageData, "page.html", Map.of());
        assertEquals("Hello", result.getString("title"));
    }

    @Test
    void extraDataAppearsInResult() {
        JsonObject pageData = new JsonObject().put("title", "Hello");
        Map<String, List<RoqFrontMatterExtraDataBuildItem>> extras = Map.of("page.html",
                List.of(new RoqFrontMatterExtraDataBuildItem("page.html",
                        new JsonObject().put("og-image", "/og/page.png"))));
        JsonObject result = RoqFrontMatterStep5RecordProcessor.mergeExtraData(pageData, "page.html", extras);
        assertEquals("/og/page.png", result.getString("og-image"));
        assertEquals("Hello", result.getString("title"));
    }

    @Test
    void userFmWins() {
        JsonObject pageData = new JsonObject().put("og-image", "/custom.png");
        Map<String, List<RoqFrontMatterExtraDataBuildItem>> extras = Map.of("page.html",
                List.of(new RoqFrontMatterExtraDataBuildItem("page.html",
                        new JsonObject().put("og-image", "/generated.png"))));
        JsonObject result = RoqFrontMatterStep5RecordProcessor.mergeExtraData(pageData, "page.html", extras);
        assertEquals("/custom.png", result.getString("og-image"));
    }

    @Test
    void multiplePluginsArraysMerge() {
        JsonObject pageData = new JsonObject().put("title", "Hello");
        Map<String, List<RoqFrontMatterExtraDataBuildItem>> extras = Map.of("page.html", List.of(
                new RoqFrontMatterExtraDataBuildItem("page.html",
                        new JsonObject().put("seoAlternates", new JsonArray().add("alt1"))),
                new RoqFrontMatterExtraDataBuildItem("page.html",
                        new JsonObject().put("seoAlternates", new JsonArray().add("alt2")))));
        JsonObject result = RoqFrontMatterStep5RecordProcessor.mergeExtraData(pageData, "page.html", extras);
        JsonArray alts = result.getJsonArray("seoAlternates");
        assertEquals(2, alts.size());
        assertEquals("alt1", alts.getString(0));
        assertEquals("alt2", alts.getString(1));
    }

    @Test
    void userFmArrayWins() {
        JsonObject pageData = new JsonObject().put("seoAlternates", new JsonArray().add("user-alt"));
        Map<String, List<RoqFrontMatterExtraDataBuildItem>> extras = Map.of("page.html",
                List.of(new RoqFrontMatterExtraDataBuildItem("page.html",
                        new JsonObject().put("seoAlternates", new JsonArray().add("plugin-alt")))));
        JsonObject result = RoqFrontMatterStep5RecordProcessor.mergeExtraData(pageData, "page.html", extras);
        JsonArray alts = result.getJsonArray("seoAlternates");
        assertEquals(2, alts.size());
    }
}
