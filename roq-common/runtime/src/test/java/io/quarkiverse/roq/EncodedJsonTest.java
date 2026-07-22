package io.quarkiverse.roq;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class EncodedJsonTest {

    @Test
    void smallObjectRoundtrips() {
        JsonObject original = new JsonObject()
                .put("title", "Hello")
                .put("count", 42);
        EncodedJson encoded = new EncodedJson(original);
        List<String> chunks = encoded.getChunks();
        assertEquals(1, chunks.size());
        assertFalse(encoded.isArray());
        EncodedJson restored = new EncodedJson(encoded.isArray(), chunks);
        assertEquals(original, restored.<JsonObject> get());
    }

    @Test
    void nestedObjectRoundtrips() {
        JsonObject original = new JsonObject()
                .put("title", "Digest")
                .put("sections", new JsonArray()
                        .add(new JsonObject()
                                .put("name", "AI")
                                .put("articles", new JsonArray()
                                        .add(new JsonObject()
                                                .put("id", "ai-1")
                                                .put("title", "Article One")
                                                .put("summary", new JsonObject()
                                                        .put("what", "Something")
                                                        .put("why", "Because"))))));
        EncodedJson encoded = new EncodedJson(original);
        EncodedJson restored = new EncodedJson(encoded.isArray(), encoded.getChunks());
        assertEquals(original, restored.<JsonObject> get());
        assertEquals("AI", restored.<JsonObject> get().getJsonArray("sections").getJsonObject(0).getString("name"));
    }

    @Test
    void largeObjectChunksCorrectly() {
        JsonObject original = new JsonObject();
        JsonArray articles = new JsonArray();
        for (int i = 0; i < 500; i++) {
            articles.add(new JsonObject()
                    .put("id", "article-" + i)
                    .put("title", "Article number " + i)
                    .put("description", "A".repeat(200))
                    .put("deep-summary", "B".repeat(300))
                    .put("decoder", "C".repeat(200)));
        }
        original.put("sections", new JsonArray().add(new JsonObject().put("name", "Test").put("articles", articles)));

        EncodedJson encoded = new EncodedJson(original);
        List<String> chunks = encoded.getChunks();
        assertTrue(chunks.size() > 1, "Large data should produce multiple chunks");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 50_000, "Each chunk must be <= 50000 chars");
        }

        EncodedJson restored = new EncodedJson(encoded.isArray(), chunks);
        assertEquals(original, restored.<JsonObject> get());
    }

    @Test
    void arrayRoundtrips() {
        JsonArray original = new JsonArray()
                .add(new JsonObject().put("id", "1").put("name", "First"))
                .add(new JsonObject().put("id", "2").put("name", "Second"));
        EncodedJson encoded = new EncodedJson(original);
        assertTrue(encoded.isArray());
        EncodedJson restored = new EncodedJson(encoded.isArray(), encoded.getChunks());
        assertEquals(original, restored.<JsonArray> get());
    }

    @Test
    void emptyObjectRoundtrips() {
        JsonObject original = new JsonObject();
        EncodedJson encoded = new EncodedJson(original);
        EncodedJson restored = new EncodedJson(encoded.isArray(), encoded.getChunks());
        assertEquals(original, restored.<JsonObject> get());
    }

    @Test
    void ofFactoryMethod() {
        JsonObject obj = new JsonObject().put("key", "value");
        JsonArray arr = new JsonArray().add("item");

        assertFalse(EncodedJson.of(obj).isArray());
        assertTrue(EncodedJson.of(arr).isArray());
        assertEquals(obj, EncodedJson.of(obj).<JsonObject> get());
        assertEquals(arr, EncodedJson.of(arr).<JsonArray> get());
    }
}
