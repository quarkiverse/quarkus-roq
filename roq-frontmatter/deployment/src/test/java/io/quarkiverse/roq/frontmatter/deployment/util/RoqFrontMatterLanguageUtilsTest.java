package io.quarkiverse.roq.frontmatter.deployment.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class RoqFrontMatterLanguageUtilsTest {

    @Test
    void defaultVariantHasNoLang() {
        var s = RoqFrontMatterLanguageUtils.parse("index.adoc", Set.of("fr")).orElseThrow();
        assertEquals("index.adoc", s.baseName());
        assertNull(s.lang());
    }

    @Test
    void translationKeepsExtensionChain() {
        var s = RoqFrontMatterLanguageUtils.parse("index.en.md", Set.of("en")).orElseThrow();
        assertEquals("en", s.lang());
        assertEquals("index.md", s.baseName());
    }

    @Test
    void quteMarkerIsNotALanguageOrIndexMarker() {
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.qute.md", Set.of("en")).isEmpty());
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.en.qute.md", Set.of("en")).isEmpty());
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.adoc.md", Set.of("en")).isEmpty());
    }

    @Test
    void translationRequiresHtmlOutputExtension() {
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.en.json", Set.of("en")).isEmpty());
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.en.qute.json", Set.of("en")).isEmpty());
        assertEquals("en", RoqFrontMatterLanguageUtils.parse("index.en.html", Set.of("en")).orElseThrow().lang());
    }

    @Test
    void markerlessIndexFilesOnly() {
        assertTrue(RoqFrontMatterLanguageUtils.isIndexFile("index.en.md", Set.of("en")));
        assertTrue(RoqFrontMatterLanguageUtils.isIndexFile("index.md", Set.of()));
        assertFalse(RoqFrontMatterLanguageUtils.isIndexFile("index.qute.md", Set.of("en")));
        assertFalse(RoqFrontMatterLanguageUtils.isIndexFile("index.en.qute.md", Set.of("en")));
        assertFalse(RoqFrontMatterLanguageUtils.isIndexFile("index.qute.md", Set.of()));
        assertFalse(RoqFrontMatterLanguageUtils.isIndexFile("index.en.json", Set.of("en")));
        assertFalse(RoqFrontMatterLanguageUtils.isIndexFile("about.md", Set.of("en")));
    }

    @Test
    void unknownSuffixIsNotATranslation() {
        assertTrue(RoqFrontMatterLanguageUtils.parse("index.v2.adoc", Set.of("fr")).isEmpty());
    }

    @Test
    void nonIndexFilesAreNotVariants() {
        assertTrue(RoqFrontMatterLanguageUtils.parse("diagram.en.svg", Set.of("en")).isEmpty());
    }

    @Test
    void parseNeverThrowsOnOddNames() {
        for (String name : new String[] { "", "index.", "index..adoc" }) {
            assertDoesNotThrow(() -> RoqFrontMatterLanguageUtils.parse(name, Set.of("en")), name);
        }
    }
}
