package io.quarkus.tools.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.RawString;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class JekyllFiltersExtensionTest {

    @Test
    void testRfc822FromLocalDateTime() {
        LocalDateTime dt = LocalDateTime.of(2024, 3, 15, 10, 30, 0);
        String result = JekyllFiltersExtension.rfc822(dt);
        assertEquals("Fri, 15 Mar 2024 10:30:00 +0000", result);
    }

    @Test
    void testRfc822AlwaysEnglish() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        String result = JekyllFiltersExtension.rfc822(dt);
        assertTrue(result.startsWith("Mon"), "Day name should be in English");
        assertTrue(result.contains("Jan"), "Month name should be in English");
    }

    @Test
    void testEscapeHtml() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;",
                JekyllFiltersExtension.escapeHtml("&<>\"'"));
    }

    @Test
    void testEscapeHtmlPlainText() {
        assertEquals("hello world", JekyllFiltersExtension.escapeHtml("hello world"));
    }

    @Test
    void testEscapeHtmlNull() {
        assertEquals("", JekyllFiltersExtension.escapeHtml(null));
    }

    @Test
    void testCapitalizeNormalString() {
        assertEquals("Hello", JekyllFiltersExtension.capitalize("hello"));
    }

    @Test
    void testCapitalizeAlreadyCapitalized() {
        assertEquals("Hello", JekyllFiltersExtension.capitalize("Hello"));
    }

    @Test
    void testCapitalizeSingleChar() {
        assertEquals("A", JekyllFiltersExtension.capitalize("a"));
    }

    @Test
    void testCapitalizeEmpty() {
        assertEquals("", JekyllFiltersExtension.capitalize(""));
    }

    @Test
    void testCapitalizeNull() {
        assertNull(JekyllFiltersExtension.capitalize(null));
    }

    @Test
    void testSplitByDot() {
        assertEquals(List.of("3", "17"), JekyllFiltersExtension.split("3.17", "."));
    }

    @Test
    void testSplitByComma() {
        assertEquals(List.of("a", "b", "c"), JekyllFiltersExtension.split("a,b,c", ","));
    }

    @Test
    void testSplitNull() {
        assertEquals(List.of(), JekyllFiltersExtension.split(null, ","));
    }

    @Test
    void testSplitEmpty() {
        assertEquals(List.of(), JekyllFiltersExtension.split("", ","));
    }

    @Test
    void testWhereExpSingleConditionGreaterThan() {
        var items = List.of(
                Map.of("urldate", "2021-09-14", "title", "A"),
                Map.of("urldate", "2021-10-01", "title", "B"),
                Map.of("urldate", "2021-08-01", "title", "C"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate > '2021-09-01'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpSingleConditionLessThanOrEqual() {
        var items = List.of(
                Map.of("urldate", "2021-09-14", "title", "A"),
                Map.of("urldate", "2021-10-01", "title", "B"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate <= '2021-09-14'");
        assertEquals(1, result.size());
    }

    @Test
    void testWhereExpMultipleConditions() {
        var items = List.of(
                Map.of("urldate", "2021-08-01"),
                Map.of("urldate", "2021-09-14"),
                Map.of("urldate", "2021-10-01"),
                Map.of("urldate", "2021-11-01"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub",
                List.of("pub.urldate > '2021-09-01'", "pub.urldate <= '2021-10-01'"));
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpNullItems() {
        var result = (List<?>) JekyllFiltersExtension.whereExp(null, "pub", "pub.date > '2021-01-01'");
        assertEquals(List.of(), result);
    }

    @Test
    void testWhereExpEquals() {
        var items = List.of(
                Map.of("type", "article"),
                Map.of("type", "video"),
                Map.of("type", "article"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "item", "item.type == 'article'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpGreaterThanOrEqual() {
        var items = List.of(
                Map.of("urldate", "2021-09-01"),
                Map.of("urldate", "2021-09-14"),
                Map.of("urldate", "2021-08-01"));
        var result = (List<?>) JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate >= '2021-09-01'");
        assertEquals(2, result.size());
    }

    @Test
    void testWhereExpJsonArrayReturnsJsonArray() {
        var items = new JsonArray()
                .add(new JsonObject().put("urldate", "2021-09-14"))
                .add(new JsonObject().put("urldate", "2021-08-01"));
        var result = JekyllFiltersExtension.whereExp(items, "pub", "pub.urldate > '2021-09-01'");
        assertInstanceOf(JsonArray.class, result);
        assertEquals(1, ((JsonArray) result).size());
    }

    @Test
    void testGroupByGroupsItemsByProperty() {
        var items = new JsonArray()
                .add(new JsonObject().put("type", "article").put("title", "A"))
                .add(new JsonObject().put("type", "video").put("title", "B"))
                .add(new JsonObject().put("type", "article").put("title", "C"))
                .add(new JsonObject().put("type", "podcast").put("title", "D"));
        var groups = JekyllFiltersExtension.groupBy(items, "type");
        assertEquals(3, groups.size());
        var articleGroup = (JsonObject) groups.stream()
                .filter(g -> "article".equals(((JsonObject) g).getString("name")))
                .findFirst().orElseThrow();
        assertEquals(2, articleGroup.getJsonArray("items").size());
    }

    @Test
    void testGroupByNullReturnsEmpty() {
        var groups = JekyllFiltersExtension.groupBy(null, "type");
        assertEquals(0, groups.size());
    }

    @Test
    void testSplitRawReturnsRawStrings() {
        var parts = JekyllFiltersExtension.splitRaw("<p>before</p>|<p>after</p>", "|");
        assertEquals(2, parts.size());
        assertInstanceOf(RawString.class, parts.get(0));
        assertInstanceOf(RawString.class, parts.get(1));
        assertEquals("<p>before</p>", parts.get(0).toString());
        assertEquals("<p>after</p>", parts.get(1).toString());
    }

    @Test
    void testMergeTypesPreservesAllFields() {
        JsonObject guide1 = new JsonObject()
                .put("title", "Getting Started")
                .put("url", "/guides/getting-started")
                .put("summary", "Your first Quarkus app")
                .put("categories", "getting-started")
                .put("type", "tutorial");
        JsonObject guide2 = new JsonObject()
                .put("title", "Building Native")
                .put("url", "/guides/building-native")
                .put("summary", "Build native executables")
                .put("categories", "native")
                .put("type", "tutorial");

        JsonObject source = new JsonObject()
                .put("types", new JsonObject()
                        .put("tutorial", new JsonArray().add(guide2).add(guide1)));
        JsonObject index = new JsonObject().put("quarkus", source);

        JsonArray result = JekyllFiltersExtension.mergeTypes(index, "tutorial");
        assertEquals(2, result.size());

        // Should be sorted by title
        JsonObject first = result.getJsonObject(0);
        assertEquals("Building Native", first.getString("title"));
        assertEquals("/guides/building-native", first.getString("url"));
        assertEquals("Build native executables", first.getString("summary"));
        assertEquals("native", first.getString("categories"));

        JsonObject second = result.getJsonObject(1);
        assertEquals("Getting Started", second.getString("title"));
        assertEquals("/guides/getting-started", second.getString("url"));
        assertEquals("Your first Quarkus app", second.getString("summary"));
    }

    @Test
    void testMergeTypesHandlesRawMapData() {
        // YAML-loaded data stores nested objects as raw Maps, not JsonObject
        Map<String, Object> guide1 = Map.of(
                "title", "Alpha Guide",
                "url", "/guides/alpha",
                "summary", "First guide");
        Map<String, Object> guide2 = Map.of(
                "title", "Beta Guide",
                "url", "/guides/beta",
                "summary", "Second guide");

        // Simulate YAML-loaded structure: types and items as raw Maps/Lists
        Map<String, Object> typesMap = Map.of("tutorial", List.of(guide2, guide1));
        Map<String, Object> sourceMap = Map.of("types", typesMap);
        JsonObject index = new JsonObject(new java.util.LinkedHashMap<>(Map.of("quarkus", sourceMap)));

        JsonArray result = JekyllFiltersExtension.mergeTypes(index, "tutorial");
        assertEquals(2, result.size());

        // Items should be wrapped as proper JsonObject instances
        JsonObject first = result.getJsonObject(0);
        assertInstanceOf(JsonObject.class, first);
        assertEquals("Alpha Guide", first.getString("title"));
        assertEquals("/guides/alpha", first.getString("url"));
        assertEquals("First guide", first.getString("summary"));

        JsonObject second = result.getJsonObject(1);
        assertEquals("Beta Guide", second.getString("title"));
        assertEquals("/guides/beta", second.getString("url"));
    }

    record TagCount(String name, Long count) {
    }

    @Test
    void testSortListOfRecordsByProperty() {
        List<?> input = List.of(
                new TagCount("quarkus", 5L),
                new TagCount("alternative", 3L),
                new TagCount("messaging", 1L));
        List<?> result = JekyllFiltersExtension.sort(input, "name");
        assertEquals(3, result.size());
        assertEquals("alternative", ((TagCount) result.get(0)).name());
        assertEquals("messaging", ((TagCount) result.get(1)).name());
        assertEquals("quarkus", ((TagCount) result.get(2)).name());
    }
}
