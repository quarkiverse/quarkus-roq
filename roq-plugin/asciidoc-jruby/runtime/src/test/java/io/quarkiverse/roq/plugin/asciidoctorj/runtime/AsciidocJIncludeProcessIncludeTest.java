package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AsciidocJIncludeProcessIncludeTest {

    // Simulates a Java source file with nested AsciiDoc tag regions
    static final String JAVA_SOURCE = """
            // tag::example[]
            package com.example;

            // tag::ignore[]
            // this line should be excluded
            // end::ignore[]

            public class Example {
                // tag::registry[]
                private final Registry registry;

                // tag::ctor[]
                Example(Registry registry) {
                    this.registry = registry;
                    // tag::gauge[]
                    registry.gauge("size", list);
                    // end::gauge[]
                }
                // end::ctor[]
                // end::registry[]

                // tag::primeMethod[]
                // tag::counted[]
                int prime() {
                    // tag::timed[]
                    return computePrime();
                    // end::timed[]
                }
                // end::counted[]
                // end::primeMethod[]
            }
            // end::example[]
            """;

    @Nested
    class SingleTag {

        @Test
        void extractsSingleTag() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tag", "ctor"));

            assertThat(result)
                    .anyMatch(l -> l.contains("Example(Registry registry)"))
                    .anyMatch(l -> l.contains("this.registry = registry;"))
                    .noneMatch(l -> l.contains("private final Registry registry;"));
        }

        @Test
        void extractsIndentedSingleTag() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tag", "gauge"));

            assertThat(result).hasSize(1)
                    .first().asString().contains("registry.gauge");
        }

        @Test
        void returnsEmptyForMissingTag() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tag", "nonexistent"));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class MultipleTags {

        @Test
        void splitsSemicolonSeparatedTags() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tags", "ctor;gauge"));

            assertThat(result)
                    .anyMatch(l -> l.contains("Example(Registry registry)"))
                    .anyMatch(l -> l.contains("registry.gauge"));
        }

        @Test
        void splitsCommaSeparatedTags() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tags", "ctor,gauge"));

            assertThat(result)
                    .anyMatch(l -> l.contains("Example(Registry registry)"))
                    .anyMatch(l -> l.contains("registry.gauge"));
        }

        @Test
        void negationWithCommas() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tags", "registry,!gauge"));

            assertThat(result)
                    .anyMatch(l -> l.contains("private final Registry registry;"))
                    .noneMatch(l -> l.contains("registry.gauge"));
        }

        @Test
        void negationExcludesTag() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE, Map.of("tags", "registry;!gauge"));

            assertThat(result)
                    .anyMatch(l -> l.contains("private final Registry registry;"))
                    .anyMatch(l -> l.contains("Example(Registry registry)"))
                    .noneMatch(l -> l.contains("registry.gauge"));
        }

        @Test
        void multipleNegations() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE,
                    Map.of("tags", "example;!ignore;!registry;!gauge;!counted;!timed;!primeMethod"));

            assertThat(result)
                    .anyMatch(l -> l.contains("package com.example;"))
                    .anyMatch(l -> l.contains("public class Example {"))
                    .noneMatch(l -> l.contains("should be excluded"))
                    .noneMatch(l -> l.contains("private final Registry"))
                    .noneMatch(l -> l.contains("registry.gauge"))
                    .noneMatch(l -> l.contains("computePrime"));
        }

        @Test
        void negationOnlyIncludesLinesOutsideExcludedTags() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(JAVA_SOURCE,
                    Map.of("tags", "primeMethod;counted;!timed"));

            assertThat(result)
                    .anyMatch(l -> l.contains("int prime()"))
                    .noneMatch(l -> l.contains("computePrime"));
        }
    }

    @Nested
    class IndentedTags {

        static final String INDENTED_SOURCE = """
                public class Foo {
                    // tag::inner[]
                    int x = 1;
                    // end::inner[]
                }
                """;

        @Test
        void matchesIndentedTagMarkers() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(INDENTED_SOURCE, Map.of("tag", "inner"));

            assertThat(result).hasSize(1)
                    .first().asString().contains("int x = 1");
        }

        @Test
        void matchesIndentedTagMarkersWithTags() throws IOException {
            List<String> result = AsciidocJInclude.processInclude(INDENTED_SOURCE, Map.of("tags", "inner"));

            assertThat(result).hasSize(1)
                    .first().asString().contains("int x = 1");
        }
    }

    @Nested
    class CommentStyles {

        @Test
        void matchesHashComments() throws IOException {
            String source = """
                    # tag::config[]
                    key=value
                    # end::config[]
                    """;
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("tag", "config"));
            assertThat(result).containsExactly("key=value");
        }

        @Test
        void matchesSemicolonComments() throws IOException {
            String source = """
                    ;; tag::init[]
                    (def x 1)
                    ;; end::init[]
                    """;
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("tag", "init"));
            assertThat(result).containsExactly("(def x 1)");
        }

        @Test
        void matchesHtmlComments() throws IOException {
            String source = """
                    <!-- tag::head[] -->
                    <title>Hello</title>
                    <!-- end::head[] -->
                    """;
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("tag", "head"));
            assertThat(result).containsExactly("<title>Hello</title>");
        }

        @Test
        void matchesDashComments() throws IOException {
            String source = """
                    -- tag::query[]
                    SELECT * FROM t;
                    -- end::query[]
                    """;
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("tag", "query"));
            assertThat(result).containsExactly("SELECT * FROM t;");
        }
    }

    @Nested
    class NoTagsAttribute {

        @Test
        void returnsAllLinesWhenNoTagSpecified() throws IOException {
            String source = "line1\nline2\nline3";
            List<String> result = AsciidocJInclude.processInclude(source, Map.of());
            assertThat(result).containsExactly("line1", "line2", "line3");
        }
    }

    @Nested
    class LineRanges {

        @Test
        void extractsSingleLine() throws IOException {
            String source = "a\nb\nc\nd";
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("lines", "2"));
            assertThat(result).containsExactly("b");
        }

        @Test
        void extractsRange() throws IOException {
            String source = "a\nb\nc\nd";
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("lines", "2..3"));
            assertThat(result).containsExactly("b", "c");
        }
    }

    @Nested
    class Indent {

        @Test
        void appliesIndent() throws IOException {
            String source = "// tag::x[]\nfoo\n// end::x[]";
            List<String> result = AsciidocJInclude.processInclude(source, Map.of("tag", "x", "indent", "4"));
            assertThat(result).containsExactly("    foo");
        }
    }
}
