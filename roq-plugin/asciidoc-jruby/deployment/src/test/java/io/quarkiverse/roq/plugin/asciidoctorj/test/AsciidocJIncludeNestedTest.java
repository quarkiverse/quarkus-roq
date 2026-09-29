package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;

public class AsciidocJIncludeNestedTest {

    private final AsciidoctorJConverter converter = new AsciidoctorJConverter(Map.of());

    @TempDir
    Path site;

    @Test
    void shouldResolveNestedIncludesRelativeToTheIncludingFile() throws IOException {
        Path sub = Files.createDirectories(site.resolve("sub"));
        Files.writeString(sub.resolve("middle.adoc"), "include::deeper.adoc[]\n");
        Files.writeString(sub.resolve("deeper.adoc"), "include::deepest.adoc[]\n");
        Files.writeString(sub.resolve("deepest.adoc"), "DEEPEST CONTENT\n");

        String html = converter.apply("include::sub/middle.adoc[]\n", Map.of(), attributesFor("index.adoc"));

        assertThat(html).contains("DEEPEST CONTENT");
        assertThat(html).doesNotContain("Unresolved directive");
    }

    private RoqTemplateAttributes attributesFor(String page) {
        return new RoqTemplateAttributes(site.toString(), site.resolve(page).toString(), null, null, null, null);
    }
}
