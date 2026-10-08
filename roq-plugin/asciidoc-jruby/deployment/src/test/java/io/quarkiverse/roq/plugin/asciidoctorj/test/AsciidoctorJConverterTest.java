package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConfig.BaseDir;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;

public class AsciidoctorJConverterTest {

    private final AsciidoctorJConverter converter = new AsciidoctorJConverter(Map.of());
    private final AsciidoctorJConverter siteConverter = new AsciidoctorJConverter(Map.of(), BaseDir.SITE);
    private final Asciidoctor asciidoctor = Asciidoctor.Factory.create();

    @Test
    void shouldSetDocnameFromSourcePath(@TempDir Path tempDir) {
        Path siteDir = tempDir.resolve("site");
        Path pageDir = siteDir.resolve("content").resolve("guides");
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir.toString(),
                pageDir.resolve("my-guide.adoc").toString(),
                null, null, null, null);

        Options options = converter.createOptions(Map.of(), attrs);
        Document doc = asciidoctor.load("= Test", options);

        assertThat(doc.getAttribute("docname")).isEqualTo("my-guide");
    }

    @Test
    void shouldSetDocnameWithMultipleDots(@TempDir Path tempDir) {
        Path siteDir = tempDir.resolve("site");
        Path postsDir = siteDir.resolve("content").resolve("posts");
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir.toString(),
                postsDir.resolve("2024-01-01-foo.bar.adoc").toString(),
                null, null, null, null);

        Options options = converter.createOptions(Map.of(), attrs);
        Document doc = asciidoctor.load("= Test", options);

        assertThat(doc.getAttribute("docname")).isEqualTo("2024-01-01-foo.bar");
    }

    @Test
    void shouldResolveIncludeFromClasspath(@TempDir Path tempDir) {
        // Use a path that doesn't exist on the filesystem,
        // so the include can only succeed through the classpath resource lookup.
        // test-includes/snippet.adoc is in src/test/resources/.
        // We derive the path from @TempDir to get a proper absolute path on all
        // platforms (bare "/foo" on Windows lacks a drive letter, causing mismatch).
        String siteDir = tempDir.resolve("nonexistent-site").toString();
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir,
                siteDir + "/content/page.adoc",
                null, null, null, null);

        String result = converter.apply(
                "include::../test-includes/snippet.adoc[]",
                Map.of(), attrs);

        assertThat(result).contains("classpath-only snippet");
    }

    @Test
    void shouldResolveMixedFilesystemAndClasspathIncludes(@TempDir Path siteDir) throws IOException {
        // A filesystem include and a classpath include in the same document.
        // The filesystem file exists on disk, the classpath one only via getResourceAsStream.
        Path contentDir = siteDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("local.adoc"), "local-file-content");
        Files.writeString(contentDir.resolve("page.adoc"), "placeholder");

        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir.toString(),
                contentDir.resolve("page.adoc").toString(),
                null, null, null, null);

        String result = converter.apply(
                """
                        include::local.adoc[]

                        include::../test-includes/snippet.adoc[]
                        """,
                Map.of(), attrs);

        assertThat(result).contains("local-file-content");
        assertThat(result).contains("classpath-only snippet");
    }

    @Test
    void shouldUsePageDirAsBaseDirByDefault(@TempDir Path tempDir) {
        // Use @TempDir to get a proper absolute path on all platforms
        Path siteDir = tempDir.resolve("site");
        Path pageDir = siteDir.resolve("content").resolve("guides");
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir.toString(),
                pageDir.resolve("my-guide.adoc").toString(),
                null, null, null, null);

        Options options = converter.createOptions(Map.of(), attrs);

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo(pageDir.toString());
        assertThat(options.map().get(AsciidoctorJConverter.PAGEDIR)).isEqualTo(pageDir.toString());
        assertThat(options.map().get(AsciidoctorJConverter.ROOTDIR)).isEqualTo(siteDir.toString());
    }

    @Test
    void shouldUseSiteDirAsBaseDirWhenConfigured(@TempDir Path tempDir) {
        // Use @TempDir to get a proper absolute path on all platforms
        Path siteDir = tempDir.resolve("site");
        Path pageDir = siteDir.resolve("content").resolve("guides");
        RoqTemplateAttributes attrs = new RoqTemplateAttributes(
                siteDir.toString(),
                pageDir.resolve("my-guide.adoc").toString(),
                null, null, null, null);

        Options options = siteConverter.createOptions(Map.of(), attrs);

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo(siteDir.toString());
        // ...while includes stay relative to the page.
        assertThat(options.map().get(AsciidoctorJConverter.PAGEDIR)).isEqualTo(pageDir.toString());
        assertThat(options.map().get(AsciidoctorJConverter.ROOTDIR)).isEqualTo(siteDir.toString());
    }

    @Test
    void shouldFallBackToPageDirWhenSiteDirIsUnknown(@TempDir Path tempDir) {
        // Use @TempDir to get a proper absolute path on all platforms
        Path pageDir = tempDir.resolve("site").resolve("content").resolve("guides");
        RoqTemplateAttributes attrs = new RoqTemplateAttributes("", pageDir.resolve("my-guide.adoc").toString(),
                null, null, null, null);

        Options options = siteConverter.createOptions(Map.of(), attrs);

        assertThat(options.map().get(Options.BASEDIR)).isEqualTo(pageDir.toString());
    }

}
