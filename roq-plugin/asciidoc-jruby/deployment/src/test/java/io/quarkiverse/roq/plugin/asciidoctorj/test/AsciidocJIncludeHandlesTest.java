package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidocJInclude;

public class AsciidocJIncludeHandlesTest {

    private final AsciidocJInclude include = new AsciidocJInclude();

    @Test
    void shouldHandleAdocFiles() {
        assertThat(include.handles("intro.adoc")).isTrue();
        assertThat(include.handles("path/to/chapter.asciidoc")).isTrue();
    }

    @Test
    void shouldHandleNonAdocFiles() {
        assertThat(include.handles("MyApp.java")).isTrue();
        assertThat(include.handles("config.yaml")).isTrue();
        assertThat(include.handles("pom.xml")).isTrue();
        assertThat(include.handles("app.properties")).isTrue();
    }

    @Test
    void shouldHandleExtensionlessFiles() {
        assertThat(include.handles("Makefile")).isTrue();
        assertThat(include.handles("Dockerfile")).isTrue();
    }

    @Test
    void shouldRejectUrls() {
        assertThat(include.handles("https://example.com/file.java")).isFalse();
        assertThat(include.handles("http://example.com/page.html")).isFalse();
        assertThat(include.handles("ftp://server/file.txt")).isFalse();
        assertThat(include.handles("mailto:user@example.com")).isFalse();
    }

    @Test
    void shouldHandleRelativeAndAbsolutePaths() {
        assertThat(include.handles("examples/src/main/java/MyApp.java")).isTrue();
        assertThat(include.handles("/absolute/path/to/file.java")).isTrue();
    }
}
