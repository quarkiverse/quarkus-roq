package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;

public class AsciidoctorJConverterTest {

    private final AsciidoctorJConverter converter = new AsciidoctorJConverter(Map.of());
    private final Asciidoctor asciidoctor = Asciidoctor.Factory.create();

    @Test
    void shouldSetDocnameFromSourcePath() {
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                "/tmp/site",
                "/tmp/site/content/guides/my-guide.adoc",
                null, null, null, null);

        Options options = converter.createOptions(Map.of(), attrs);
        Document doc = asciidoctor.load("= Test", options);

        assertThat(doc.getAttribute("docname")).isEqualTo("my-guide");
    }

    @Test
    void shouldSetDocnameWithMultipleDots() {
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                "/tmp/site",
                "/tmp/site/content/posts/2024-01-01-foo.bar.adoc",
                null, null, null, null);

        Options options = converter.createOptions(Map.of(), attrs);
        Document doc = asciidoctor.load("= Test", options);

        assertThat(doc.getAttribute("docname")).isEqualTo("2024-01-01-foo.bar");
    }

}
