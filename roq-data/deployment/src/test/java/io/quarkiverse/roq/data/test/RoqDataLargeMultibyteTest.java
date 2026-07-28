package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

/**
 * Test for issue #1133: "UTF8 string too large" when building a site with multibyte content in YAML data files.
 *
 * The JVM constant pool has a 65,535-byte limit for UTF-8 strings. When Japanese or other multibyte characters
 * are used (3-4 bytes per character in UTF-8), a large string can exceed this limit if chunking is done by
 * character count instead of byte count.
 */
public class RoqDataLargeMultibyteTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> {
                // Create a large YAML file with multibyte content (Japanese characters)
                // Each Japanese character is typically 3 bytes in UTF-8
                // Create a large single string value to trigger the bytecode constant pool limit
                // The JVM limit is 65,535 bytes; with 3-byte chars, ~21,000 chars would exceed it
                StringBuilder largeText = new StringBuilder();
                for (int i = 0; i < 25000; i++) {
                    largeText.append("日本語"); // 9 bytes (3 chars × 3 bytes each)
                }

                StringBuilder yaml = new StringBuilder();
                yaml.append("largeValue: \"").append(largeText).append("\"\nitems:\n");
                for (int i = 0; i < 100; i++) {
                    yaml.append("  - text: \"日本語テキスト番号").append(i).append("\"\n");
                    yaml.append("    description: \"これは説明文です。マルチバイト文字のテストです。\"\n");
                }

                jar.add(new StringAsset(yaml.toString()),
                        "data/large-multibyte.yaml")
                        .add(new StringAsset("quarkus.roq.dir=."),
                                "application.properties");
            });

    @Inject
    @Named("large-multibyte")
    JsonObject largeMultibyteData;

    @Test
    public void testLargeMultibyteDataLoads() {
        Assertions.assertNotNull(largeMultibyteData, "Large multibyte data should be loaded");
        Assertions.assertTrue(largeMultibyteData.containsKey("items"), "Data should contain 'items' key");
        Assertions.assertTrue(largeMultibyteData.containsKey("largeValue"), "Data should contain 'largeValue' key");

        // Verify large value is correctly loaded (75,000 characters, 225,000 bytes in UTF-8)
        String largeValue = largeMultibyteData.getString("largeValue");
        Assertions.assertNotNull(largeValue, "Large value should not be null");
        Assertions.assertEquals(75000, largeValue.length(), "Large value should have 75,000 characters");

        // Verify the content is correctly decoded
        JsonObject firstItem = largeMultibyteData.getJsonArray("items").getJsonObject(0);
        Assertions.assertEquals("日本語テキスト番号0", firstItem.getString("text"));
        Assertions.assertEquals("これは説明文です。マルチバイト文字のテストです。", firstItem.getString("description"));
    }
}
