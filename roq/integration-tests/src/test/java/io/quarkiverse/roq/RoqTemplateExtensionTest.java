package io.quarkiverse.roq;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;

public class RoqTemplateExtensionTest {
    @Test
    public void testAsStrings() {
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1 tag2 tag3 tag-four").size());
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1,tag2,tag3,tag-four").size());
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1, tag2, tag3, tag-four").size());
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1;tag2;tag3;tag-four").size());
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1; tag2; tag3; tag-four").size());
        Assertions.assertEquals(4,
                RoqTemplateExtension.asStrings("tag1\ttag2\ttag3\ttag-four").size());
    }
}
