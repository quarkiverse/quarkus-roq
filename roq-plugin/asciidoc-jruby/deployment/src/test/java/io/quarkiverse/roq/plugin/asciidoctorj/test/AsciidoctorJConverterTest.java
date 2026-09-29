package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConfig.BaseDir;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;

public class AsciidoctorJConverterTest {

    private static final RoqTemplateAttributes ATTRS = new RoqTemplateAttributes(
            "/tmp/site",
            "/tmp/site/content/guides/my-guide.adoc",
            null, null, null, null);

    private final AsciidoctorJConverter converter = new AsciidoctorJConverter(Map.of());
    private final AsciidoctorJConverter siteConverter = new AsciidoctorJConverter(Map.of(), BaseDir.SITE);
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

    @Test
    void shouldUsePageDirAsBaseDirByDefault() {
        Options options = converter.createOptions(Map.of(), ATTRS);

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo("/tmp/site/content/guides");
        assertThat(options.map().get(AsciidoctorJConverter.PAGEDIR)).isEqualTo("/tmp/site/content/guides");
        assertThat(options.map().get(AsciidoctorJConverter.ROOTDIR)).isEqualTo("/tmp/site");
    }

    @Test
    void shouldUseSiteDirAsBaseDirWhenConfigured() {
        // base_dir is the safe-mode jail, so rooting it at the site dir is what lets asciidoctor-diagram
        // read .puml sources from, and write images to, directories other than the page's own.
        Options options = siteConverter.createOptions(Map.of(), ATTRS);

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo("/tmp/site");
        // ...while includes stay relative to the page.
        assertThat(options.map().get(AsciidoctorJConverter.PAGEDIR)).isEqualTo("/tmp/site/content/guides");
        assertThat(options.map().get(AsciidoctorJConverter.ROOTDIR)).isEqualTo("/tmp/site");
    }

    @Test
    void shouldFallBackToPageDirWhenSiteDirIsUnknown() {
        Options options = siteConverter.createOptions(Map.of(),
                new RoqTemplateAttributes("", "/tmp/site/content/guides/my-guide.adoc", null, null, null, null));

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo("/tmp/site/content/guides");
    }

}
