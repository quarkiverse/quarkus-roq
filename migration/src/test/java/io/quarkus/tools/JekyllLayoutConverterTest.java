package io.quarkus.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class JekyllLayoutConverterTest {

    private final JekyllLayoutConverter converter = new JekyllLayoutConverter();

    @Test
    void testSingleAssetsCssReplaced() throws IOException {
        String input = """
                <head>
                  <title>Test</title>
                  <link rel="stylesheet" href="/guides/stylesheet/config.css" />
                  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}?2021-07-29" />
                  <link rel="stylesheet" href="https://use.fontawesome.com/releases/v6.5.2/css/all.css" crossorigin="anonymous">
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should have bundle tag
        assertTrue(result.contains("{#bundle /}"), "Should contain bundle tag");

        // Should NOT have assets/css link
        assertFalse(result.contains("/assets/css/"), "Should not contain /assets/css/ link");

        // Should preserve config.css
        assertTrue(result.contains("/guides/stylesheet/config.css"), "Should preserve config.css");

        // Should preserve external CSS
        assertTrue(result.contains("fontawesome"), "Should preserve external CSS");
    }

    @Test
    void testMultipleAssetsCssLinks() throws IOException {
        String input = """
                <head>
                  <title>Test</title>
                  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}" />
                  <link rel="stylesheet" href="{{ '/assets/css/custom.css' | relative_url }}" />
                  <link rel="stylesheet" href="{{ '/assets/css/theme.css' | relative_url }}" />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should have exactly one bundle tag
        int bundleCount = result.split("\\{#bundle /\\}", -1).length - 1;
        assertEquals(1, bundleCount, "Should have exactly one bundle tag");

        // Should NOT have any assets/css links
        assertFalse(result.contains("/assets/css/"), "Should not contain any /assets/css/ links");
    }

    @Test
    void testPreservesIndentation() throws IOException {
        String input = """
                <head>
                  <title>Test</title>
                    <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}" />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should preserve the indentation (4 spaces)
        assertTrue(result.contains("    {#bundle /}"), "Should preserve indentation");
    }

    @Test
    void testNoAssetsCssLinks() throws IOException {
        String input = """
                <head>
                  <title>Test</title>
                  <link rel="stylesheet" href="/custom/style.css" />
                  <link rel="stylesheet" href="https://cdn.example.com/style.css" />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should NOT add bundle tag if no /assets/css/ links
        assertFalse(result.contains("{#bundle /}"), "Should not add bundle tag if no /assets/css/ links");

        // Should preserve all links
        assertTrue(result.contains("/custom/style.css"), "Should preserve custom CSS");
        assertTrue(result.contains("cdn.example.com"), "Should preserve CDN CSS");
    }

    @Test
    void testMixedAssetsAndNonAssets() throws IOException {
        String input = """
                <head>
                  <link rel="stylesheet" href="/guides/stylesheet/config.css" />
                  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}" />
                  <link rel="stylesheet" href="/custom/style.css" />
                  <link rel="stylesheet" href="{{ '/assets/css/extra.css' | relative_url }}" />
                  <link rel="stylesheet" href="https://fonts.googleapis.com/css" />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should have one bundle tag
        assertTrue(result.contains("{#bundle /}"), "Should contain bundle tag");

        // Should NOT have assets/css links
        assertFalse(result.contains("/assets/css/"), "Should not contain /assets/css/ links");

        // Should preserve non-assets links
        assertTrue(result.contains("/guides/stylesheet/config.css"), "Should preserve config.css");
        assertTrue(result.contains("/custom/style.css"), "Should preserve custom CSS");
        assertTrue(result.contains("fonts.googleapis.com"), "Should preserve Google Fonts");
    }

    @Test
    void testDifferentQuoteStyles() throws IOException {
        String input = """
                <head>
                  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}" />
                  <link rel='stylesheet' href='{{ "/assets/css/alt.css" | relative_url }}' />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Should handle both quote styles
        assertFalse(result.contains("/assets/css/"), "Should handle both single and double quotes");
        assertTrue(result.contains("{#bundle /}"), "Should contain bundle tag");
    }

    @Test
    void testRealWorldQuarkusioExample() throws IOException {
        // Real example from quarkusio.github.io
        String input = """
                <head>
                  <title>{{ page.title }}</title>
                  <meta charset="utf-8">
                  <link rel="canonical" href="{{ canonical_url | prepend: site.url }}">
                  <link rel="shortcut icon" type="image/png" href="/favicon.ico" >
                  <link rel="stylesheet" href="/guides/stylesheet/config.css" />
                  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}?2021-07-29" />
                  <link rel="stylesheet" href="https://use.fontawesome.com/releases/v6.5.2/css/all.css" crossorigin="anonymous">
                  <link rel="alternate" type="application/rss+xml" href="/feed.xml" />
                </head>
                """;

        String result = converter.replaceAssetsCssWithBundle(input);

        // Expected output
        String expected = """
                <head>
                  <title>{{ page.title }}</title>
                  <meta charset="utf-8">
                  <link rel="canonical" href="{{ canonical_url | prepend: site.url }}">
                  <link rel="shortcut icon" type="image/png" href="/favicon.ico" >
                  <link rel="stylesheet" href="/guides/stylesheet/config.css" />
                  {#bundle /}
                  <link rel="stylesheet" href="https://use.fontawesome.com/releases/v6.5.2/css/all.css" crossorigin="anonymous">
                  <link rel="alternate" type="application/rss+xml" href="/feed.xml" />
                </head>
                """;

        assertEquals(expected, result, "Should match expected real-world output");
    }
}
