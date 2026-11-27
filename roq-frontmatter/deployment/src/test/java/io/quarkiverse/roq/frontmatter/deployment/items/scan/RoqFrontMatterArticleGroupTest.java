package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Roq FrontMatter - Article group record")
public class RoqFrontMatterArticleGroupTest {

    private static RoqFrontMatterScannedContentBuildItem item() {
        return new RoqFrontMatterScannedContentBuildItem(null, null, false, false, List.of());
    }

    @Test
    public void translationsKeepInsertionOrderAndAreUnmodifiableCopy() {
        Map<String, RoqFrontMatterScannedContentBuildItem> source = new LinkedHashMap<>();
        source.put("fr", item());
        source.put("de", item());
        source.put("es", item());

        RoqFrontMatterArticleGroup group = new RoqFrontMatterArticleGroup(Path.of("posts/hello"), item(), source);

        assertEquals(List.of("fr", "de", "es"), new ArrayList<>(group.translations().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> group.translations().put("it", item()));

        source.put("it", item());
        assertEquals(3, group.translations().size());
    }
}
