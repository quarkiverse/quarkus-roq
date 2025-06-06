package io.quarkiverse.roq;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;

public class RoqTemplateExtensionTest {
    @Test
    public void testAsStrings() {
        var tags = List.of("tag1", "tag2", "tag3", "tag-four");
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1 tag2 tag3 tag-four"));
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1,tag2,tag3,tag-four"));
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1, tag2, tag3, tag-four"));
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1;tag2;tag3;tag-four"));
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1; tag2; tag3; tag-four"));
        Assertions.assertEquals(tags,
                RoqTemplateExtension.asStrings("tag1\ttag2\ttag3\ttag-four"));
    }
}
