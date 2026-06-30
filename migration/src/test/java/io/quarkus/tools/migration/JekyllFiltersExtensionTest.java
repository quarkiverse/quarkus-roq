package io.quarkus.tools.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class JekyllFiltersExtensionTest {

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
}
