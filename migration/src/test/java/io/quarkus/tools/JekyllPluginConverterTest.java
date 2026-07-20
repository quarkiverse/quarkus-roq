package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JekyllPluginConverterTest {

    @TempDir
    Path projectDir;

    Path pluginsDir;
    Path srcDir;

    @BeforeEach
    void setUp() throws IOException {
        pluginsDir = projectDir.resolve("_plugins");
        Files.createDirectories(pluginsDir);
        srcDir = projectDir.resolve("src/main/java/io/quarkus/tools/migration");
        Files.createDirectories(srcDir);
    }

    // --- Generic GitHub Pages plugin (HANDLED) ---

    @Test
    void testCnamePluginIsHandled() throws IOException {
        Files.writeString(pluginsDir.resolve("cname.rb"),
                "module Jekyll\n  class LimitedEnvironmentVariables < Generator\n  end\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactly("cname.rb");
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Unknown plugins fail by default (MANUAL) ---

    @Test
    void testUnknownPluginFailsByDefault() throws IOException {
        Files.writeString(pluginsDir.resolve("custom-plugin.rb"),
                "module Jekyll\n  module CustomFilter\n  end\nend");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactly("custom-plugin.rb");
        assertThat(result.handled()).isEmpty();
        assertThat(result.failureMessages().get("custom-plugin.rb"))
                .contains("custom-plugin.rb")
                .contains("CustomPlugin.java");
    }

    @Test
    void testMultipleUnknownPluginsFail() throws IOException {
        Files.writeString(pluginsDir.resolve("plugin-a.rb"), "# plugin a");
        Files.writeString(pluginsDir.resolve("plugin-b.rb"), "# plugin b");
        Files.writeString(pluginsDir.resolve("plugin-c.rb"), "# plugin c");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactlyInAnyOrder(
                "plugin-a.rb", "plugin-b.rb", "plugin-c.rb");
        assertThat(result.handled()).isEmpty();
    }

    // --- Plugins with hand-coded equivalents are skipped ---

    @Test
    void testPluginSkipsWhenEquivalentExists() throws IOException {
        Files.writeString(pluginsDir.resolve("my-filter.rb"),
                "module Jekyll\n  module MyFilter\n  end\nend");

        // Hand-code the equivalent
        Files.writeString(srcDir.resolve("MyFilter.java"),
                "package io.quarkus.tools.migration;\npublic class MyFilter {}");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).isEmpty();
        assertThat(result.skipped()).containsExactly("my-filter.rb");
    }

    @Test
    void testMultiplePluginsWithEquivalents() throws IOException {
        Files.writeString(pluginsDir.resolve("filter-a.rb"), "# filter a");
        Files.writeString(pluginsDir.resolve("filter-b.rb"), "# filter b");
        Files.writeString(pluginsDir.resolve("filter-c.rb"), "# filter c");

        // Provide equivalents for some
        Files.writeString(srcDir.resolve("FilterA.java"),
                "package io.quarkus.tools.migration;\npublic class FilterA {}");
        Files.writeString(srcDir.resolve("FilterC.java"),
                "package io.quarkus.tools.migration;\npublic class FilterC {}");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.skipped()).containsExactlyInAnyOrder("filter-a.rb", "filter-c.rb");
        assertThat(result.failed()).containsExactly("filter-b.rb");
    }

    // --- Ruby filename to Java class name conversion ---

    @Test
    void testRubyFileNameToJavaClassConversion() throws IOException {
        Files.writeString(pluginsDir.resolve("my-custom-filter.rb"), "# custom filter");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactly("my-custom-filter.rb");
        assertThat(result.failureMessages().get("my-custom-filter.rb"))
                .contains("MyCustomFilter.java");
    }

    @Test
    void testUnderscoreFileNameConversion() throws IOException {
        Files.writeString(pluginsDir.resolve("some_long_plugin_name.rb"), "# plugin");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.failed()).containsExactly("some_long_plugin_name.rb");
        assertThat(result.failureMessages().get("some_long_plugin_name.rb"))
                .contains("SomeLongPluginName.java");
    }

    // --- No plugins directory ---

    @Test
    void testNoPluginsDirSucceeds() throws IOException {
        Files.delete(pluginsDir);

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Empty plugins directory ---

    @Test
    void testEmptyPluginsDirSucceeds() throws IOException {
        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Non-ruby files are ignored ---

    @Test
    void testNonRubyFilesIgnored() throws IOException {
        Files.writeString(pluginsDir.resolve("readme.md"), "# Plugins");
        Files.writeString(pluginsDir.resolve("helper.txt"), "notes");
        Files.writeString(pluginsDir.resolve(".gitignore"), "*.tmp");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).isEmpty();
        assertThat(result.translated()).isEmpty();
        assertThat(result.failed()).isEmpty();
    }

    // --- Mixed scenario ---

    @Test
    void testMixedScenario() throws IOException {
        // Generic GitHub Pages plugin (handled)
        Files.writeString(pluginsDir.resolve("cname.rb"), "# cname");

        // Unknown plugins (will fail)
        Files.writeString(pluginsDir.resolve("custom-filter.rb"), "# custom filter");
        Files.writeString(pluginsDir.resolve("search-plugin.rb"), "# search");

        // Unknown plugin with hand-coded equivalent (skipped)
        Files.writeString(pluginsDir.resolve("extensions.rb"), "# extensions");
        Files.writeString(srcDir.resolve("Extensions.java"),
                "package io.quarkus.tools.migration;\npublic class Extensions {}");

        // Non-ruby file (ignored)
        Files.writeString(pluginsDir.resolve("notes.txt"), "notes");

        JekyllPluginConverter converter = new JekyllPluginConverter(projectDir);
        JekyllPluginConverter.Result result = converter.convert();

        assertThat(result.handled()).containsExactly("cname.rb");
        assertThat(result.translated()).isEmpty();
        assertThat(result.skipped()).containsExactly("extensions.rb");
        assertThat(result.failed()).containsExactlyInAnyOrder(
                "custom-filter.rb", "search-plugin.rb");
    }
}
