package io.quarkiverse.roq;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class LargeRecordsTest {

    @Test
    void serializeRoundTripString() throws Exception {
        byte[] bytes = LargeRecords.serialize("hello");
        assertEquals("hello", deserialize(bytes));
    }

    @Test
    void serializeRoundTripList() throws Exception {
        List<String> original = List.of("a", "b", "c");
        byte[] bytes = LargeRecords.serialize(original);
        assertEquals(original, deserialize(bytes));
    }

    @Test
    void serializeRoundTripRecord() throws Exception {
        TestRecord original = new TestRecord("title", 42);
        byte[] bytes = LargeRecords.serialize(original);
        assertEquals(original, deserialize(bytes));
    }

    @Test
    void serializeRoundTripNestedRecords() throws Exception {
        TestRecord inner = new TestRecord("inner", 1);
        List<TestRecord> original = List.of(inner, new TestRecord("other", 2));
        byte[] bytes = LargeRecords.serialize(original);
        assertEquals(original, deserialize(bytes));
    }

    @Test
    void serializeJsonObjectRoundTrip() throws Exception {
        JsonObject original = new JsonObject()
                .put("title", "Hello")
                .put("count", 42)
                .put("nested", new JsonObject().put("key", "value"));
        byte[] bytes = LargeRecords.serializeJson(original);
        JsonObject restored = deserializeJson(bytes);
        assertEquals(original, restored);
    }

    @Test
    void serializeJsonArrayRoundTrip() throws Exception {
        JsonArray original = new JsonArray()
                .add(new JsonObject().put("id", "1").put("name", "First"))
                .add(new JsonObject().put("id", "2").put("name", "Second"));
        byte[] bytes = LargeRecords.serializeJson(original);
        JsonArray restored = deserializeJson(bytes);
        assertEquals(original, restored);
    }

    @Test
    void serializeJsonEmptyObject() throws Exception {
        JsonObject original = new JsonObject();
        byte[] bytes = LargeRecords.serializeJson(original);
        assertEquals(original, deserializeJson(bytes));
    }

    @Test
    void serializeJsonMultibyteContent() throws Exception {
        JsonObject original = new JsonObject()
                .put("text", "日本語テキスト")
                .put("emoji", "Hello 🌍");
        byte[] bytes = LargeRecords.serializeJson(original);
        assertEquals(original, deserializeJson(bytes));
    }

    @Test
    void serializeJsonLargePayload() throws Exception {
        JsonArray articles = new JsonArray();
        for (int i = 0; i < 500; i++) {
            articles.add(new JsonObject()
                    .put("id", "article-" + i)
                    .put("title", "Article number " + i)
                    .put("description", "A".repeat(200)));
        }
        JsonObject original = new JsonObject().put("articles", articles);
        byte[] bytes = LargeRecords.serializeJson(original);
        assertEquals(original, deserializeJson(bytes));
    }

    @Test
    void toSerializableConvertsNestedJsonTypes() {
        JsonObject original = new JsonObject()
                .put("name", "test")
                .put("nested", new JsonObject().put("inner", "value"))
                .put("list", new JsonArray().add("a").add(new JsonObject().put("b", 1)));
        Map<String, Object> result = LargeRecords.toSerializable(original);
        assertInstanceOf(Map.class, result.get("nested"));
        assertInstanceOf(List.class, result.get("list"));
        assertInstanceOf(Serializable.class, result);
    }

    @Test
    void serializeJsonRejectsNonJsonTypes() {
        assertThrows(IllegalArgumentException.class, () -> LargeRecords.serializeJson("not json"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeJson(byte[] bytes) throws Exception {
        Object data = deserialize(bytes);
        if (data instanceof Map<?, ?> map) {
            return (T) new JsonObject((Map<String, Object>) map);
        }
        return (T) new JsonArray((List<Object>) data);
    }

    record TestRecord(String name, int value) implements Serializable {
    }
}
