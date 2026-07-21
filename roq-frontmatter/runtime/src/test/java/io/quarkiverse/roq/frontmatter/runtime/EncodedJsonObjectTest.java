package io.quarkiverse.roq.frontmatter.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class EncodedJsonObjectTest {

    @Test
    void smallDataRoundtrips() {
        JsonObject original = new JsonObject()
                .put("title", "Hello")
                .put("count", 42);
        EncodedJsonObject encoded = new EncodedJsonObject(original);
        List<String> chunks = encoded.getChunks();
        assertEquals(1, chunks.size());
        EncodedJsonObject restored = new EncodedJsonObject(chunks);
        assertEquals(original, restored.get());
    }

    @Test
    void nestedDataRoundtrips() {
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
        EncodedJsonObject encoded = new EncodedJsonObject(original);
        EncodedJsonObject restored = new EncodedJsonObject(encoded.getChunks());
        assertEquals(original, restored.get());
        assertEquals("AI", restored.get().getJsonArray("sections").getJsonObject(0).getString("name"));
    }

    @Test
    void largeDataChunksCorrectly() {
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

        EncodedJsonObject encoded = new EncodedJsonObject(original);
        List<String> chunks = encoded.getChunks();
        assertTrue(chunks.size() > 1, "Large data should produce multiple chunks");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 50_000, "Each chunk must be <= 50000 chars");
        }

        EncodedJsonObject restored = new EncodedJsonObject(chunks);
        assertEquals(original, restored.get());
    }

    @Test
    void emptyDataRoundtrips() {
        JsonObject original = new JsonObject();
        EncodedJsonObject encoded = new EncodedJsonObject(original);
        EncodedJsonObject restored = new EncodedJsonObject(encoded.getChunks());
        assertEquals(original, restored.get());
    }
}
