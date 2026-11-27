package io.quarkiverse.roq.frontmatter.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void fromCodeIsExactAndNullSafe() {
        assertEquals("🇫🇷", Language.fromCode("fr").orElseThrow().flag());
        assertTrue(Language.fromCode("FR").isEmpty());
        assertTrue(Language.fromCode("xx").isEmpty());
        assertTrue(Language.fromCode(null).isEmpty());
    }
}
