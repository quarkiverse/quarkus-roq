package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.TemplateContext;
import io.vertx.core.json.JsonObject;

class AsciidocHeaderParserTest {

    private final RoqFrontMatterHeaderParserBuildItem buildItem = AsciidocHeaderParser.createBuildItem(false,
            ctx -> true);

    private JsonObject parse(String content) {
        TemplateContext ctx = new TemplateContext(Path.of("/tmp/test.adoc"), "test.adoc", content);
        return buildItem.parse().apply(ctx);
    }

    // --- Original reproducer tests (crash prevention) ---

    @Test
    void shouldParseHeaderWithIfdefInAttributes() {
        String content = """
                = My Title
                :page-layout: post
                ifdef::backend-html5[]
                :page-foo: bar
                endif::[]

                Some content here.
                """;

        JsonObject result = parse(content);
        assertThat(result.getString("title")).isEqualTo("My Title");
        assertThat(result.getString("layout")).isEqualTo("post");
    }

    @Test
    void shouldParseHeaderWithFrontmatterAndIfdef() {
        String content = """
                ---
                layout: post
                ---
                = My Title
                ifdef::backend-html5[]
                :icons: font
                endif::[]

                Some content here.
                """;

        JsonObject result = parse(content);
        assertThat(result.getString("title")).isEqualTo("My Title");
    }

    @Test
    void shouldParseHeaderWithIfdefContainingInclude() {
        String content = """
                = My Title
                :page-layout: post
                ifdef::env-github[]
                include::_attributes.adoc[]
                endif::[]

                Some content here.
                """;

        JsonObject result = parse(content);
        assertThat(result.getString("title")).isEqualTo("My Title");
        assertThat(result.getString("layout")).isEqualTo("post");
    }

    @Test
    void shouldParseHeaderWithInlineIfdef() {
        String content = """
                = My Title
                ifdef::env-github,env-browser,env-vscode[:imagesdir: ../assets/images/posts]

                Some content here.
                """;

        JsonObject result = parse(content);
        assertThat(result.getString("title")).isEqualTo("My Title");
    }

    @Test
    void shouldParseHeaderWithMultipleInlineIfdefs() {
        String content = """
                = My Title
                :page-layout: post
                ifdef::env-github[:tip-caption: :bulb:]
                ifdef::env-browser[:icons: font]

                Some content here.
                """;

        JsonObject result = parse(content);
        assertThat(result.getString("title")).isEqualTo("My Title");
        assertThat(result.getString("layout")).isEqualTo("post");
    }

    // --- Condition evaluation tests ---

    @Nested
    class IfdefWithDefinedAttribute {

        @Test
        void shouldIncludeBlockContentWhenAttributeIsDefined() {
            String content = """
                    = My Title
                    :my-flag:
                    ifdef::my-flag[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldIncludeInlineContentWhenAttributeIsDefined() {
            String content = """
                    = My Title
                    :my-flag:
                    ifdef::my-flag[:page-included: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }
    }

    @Nested
    class IfdefWithUndefinedAttribute {

        @Test
        void shouldExcludeBlockContentWhenAttributeIsNotDefined() {
            String content = """
                    = My Title
                    ifdef::not-defined[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldExcludeInlineContentWhenAttributeIsNotDefined() {
            String content = """
                    = My Title
                    ifdef::not-defined[:page-excluded: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }
    }

    @Nested
    class IfndefWithDefinedAttribute {

        @Test
        void shouldExcludeBlockContentWhenAttributeIsDefined() {
            String content = """
                    = My Title
                    :my-flag:
                    ifndef::my-flag[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldExcludeInlineContentWhenAttributeIsDefined() {
            String content = """
                    = My Title
                    :my-flag:
                    ifndef::my-flag[:page-excluded: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }
    }

    @Nested
    class IfndefWithUndefinedAttribute {

        @Test
        void shouldIncludeBlockContentWhenAttributeIsNotDefined() {
            String content = """
                    = My Title
                    ifndef::not-defined[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldIncludeInlineContentWhenAttributeIsNotDefined() {
            String content = """
                    = My Title
                    ifndef::not-defined[:page-included: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }
    }

    @Nested
    class MultiAttributeConditions {

        @Test
        void ifdefOrShouldIncludeWhenAnyAttributeIsDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    ifdef::flag-a,flag-b[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void ifdefOrShouldExcludeWhenNoAttributeIsDefined() {
            String content = """
                    = My Title
                    ifdef::flag-a,flag-b[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void ifdefAndShouldIncludeWhenAllAttributesAreDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    :flag-b:
                    ifdef::flag-a+flag-b[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void ifdefAndShouldExcludeWhenNotAllAttributesAreDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    ifdef::flag-a+flag-b[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void ifndefOrShouldIncludeWhenNoAttributeIsDefined() {
            // ifndef with comma = NOR: include only if NONE are defined
            String content = """
                    = My Title
                    ifndef::flag-a,flag-b[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void ifndefOrShouldExcludeWhenAnyAttributeIsDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    ifndef::flag-a,flag-b[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void ifndefAndShouldIncludeWhenNotAllAttributesAreDefined() {
            // ifndef with plus = NAND: include if NOT ALL are defined
            String content = """
                    = My Title
                    :flag-a:
                    ifndef::flag-a+flag-b[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void ifndefAndShouldExcludeWhenAllAttributesAreDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    :flag-b:
                    ifndef::flag-a+flag-b[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void inlineIfdefOrShouldIncludeWhenAnyDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    ifdef::flag-a,flag-b[:page-included: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void inlineIfdefAndShouldExcludeWhenNotAllDefined() {
            String content = """
                    = My Title
                    :flag-a:
                    ifdef::flag-a+flag-b[:page-excluded: yes]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }
    }

    @Nested
    class Nesting {

        @Test
        void shouldIncludeContentWhenBothOuterAndInnerAreTrue() {
            String content = """
                    = My Title
                    :outer:
                    :inner:
                    ifdef::outer[]
                    ifdef::inner[]
                    :page-included: yes
                    endif::[]
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldExcludeContentWhenOuterIsFalse() {
            String content = """
                    = My Title
                    :inner:
                    ifdef::outer[]
                    ifdef::inner[]
                    :page-excluded: yes
                    endif::[]
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldExcludeContentWhenInnerIsFalse() {
            String content = """
                    = My Title
                    :outer:
                    ifdef::outer[]
                    ifdef::inner[]
                    :page-excluded: yes
                    endif::[]
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldNotDefineAttributesInsideExcludedBlock() {
            String content = """
                    = My Title
                    ifdef::not-defined[]
                    :sneaky-attr:
                    endif::[]
                    ifdef::sneaky-attr[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }
    }

    @Nested
    class AttributeTracking {

        @Test
        void shouldTrackAttributeWithValue() {
            String content = """
                    = My Title
                    :backend: html5
                    ifdef::backend[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldTrackAttributeWithEmptyValue() {
            String content = """
                    = My Title
                    :my-flag:
                    ifdef::my-flag[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldUnsetAttributeWithLeadingBang() {
            String content = """
                    = My Title
                    :my-flag:
                    :!my-flag:
                    ifdef::my-flag[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldUnsetAttributeWithTrailingBang() {
            String content = """
                    = My Title
                    :my-flag:
                    :my-flag!:
                    ifdef::my-flag[]
                    :page-excluded: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
        }

        @Test
        void shouldTrackAttributeDefinedInsideIncludedBlock() {
            String content = """
                    = My Title
                    :first:
                    ifdef::first[]
                    :second:
                    endif::[]
                    ifdef::second[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }

        @Test
        void shouldTrackAttributeDefinedByInlineIfdef() {
            String content = """
                    = My Title
                    :first:
                    ifdef::first[:second:]
                    ifdef::second[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("included")).isEqualTo("yes");
        }
    }

    @Nested
    class IfdefBeforeTitle {

        @Test
        void shouldParseTitleWhenIfdefBeforeTitleEvaluatesToFalse() {
            // When the condition is false, the orphan content is stripped
            // and the title is found correctly
            String content = """
                    ifdef::env-github[]
                    :tip-caption: :bulb:
                    endif::[]
                    = My Title
                    :page-layout: post

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("title")).isEqualTo("My Title");
            assertThat(result.getString("layout")).isEqualTo("post");
        }
    }

    @Nested
    class EnvironmentAttributes {

        // env-* attributes (env-github, env-browser, env-vscode, env-site) are set by
        // external rendering environments, not by the document itself. Since Roq is its
        // own SSG, none of these apply — they are always undefined.

        @Test
        void envAttributesAreUndefined() {
            String content = """
                    = My Title
                    ifdef::env-github,env-browser,env-vscode[:page-excluded: yes]
                    ifndef::env-github[]
                    :page-included: yes
                    endif::[]

                    Body.
                    """;

            JsonObject result = parse(content);
            assertThat(result.getString("excluded")).isNull();
            assertThat(result.getString("included")).isEqualTo("yes");
        }
    }

    // --- stripConditionalDirectives unit tests ---

    @Nested
    class StripConditionalDirectives {

        @Test
        void shouldPassThroughContentWithNoDirectives() {
            String content = """
                    = My Title
                    :page-layout: post

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).contains("= My Title");
            assertThat(result).contains(":page-layout: post");
        }

        @Test
        void shouldStripIfeval() {
            String content = """
                    = My Title
                    ifeval::["{backend}" == "html5"]
                    :page-foo: bar
                    endif::[]

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).doesNotContain("ifeval");
            assertThat(result).contains("= My Title");
        }

        @Test
        void shouldNotLetIfevalEndifStealEnclosingIfdefEntry() {
            String content = """
                    = My Title
                    :my-flag:
                    ifdef::my-flag[]
                    ifeval::["{backend}" == "html5"]
                    :page-inner: yes
                    endif::[]
                    :page-outer: yes
                    endif::[]

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).contains(":page-inner: yes");
            assertThat(result).contains(":page-outer: yes");
            assertThat(result).doesNotContain("ifdef");
            assertThat(result).doesNotContain("endif");
        }

        @Test
        void shouldExcludeIfevalContentWhenInsideExcludedIfdef() {
            String content = """
                    = My Title
                    ifdef::not-defined[]
                    ifeval::["{backend}" == "html5"]
                    :page-excluded: yes
                    endif::[]
                    :page-also-excluded: yes
                    endif::[]

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).doesNotContain(":page-excluded:");
            assertThat(result).doesNotContain(":page-also-excluded:");
        }

        @Test
        void shouldPassThroughEscapedDirective() {
            String content = """
                    = My Title
                    \\ifdef::some-attr[]
                    :page-included: yes
                    endif::[]

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).contains("ifdef::some-attr[]");
            assertThat(result).doesNotContain("\\ifdef");
            assertThat(result).contains(":page-included: yes");
        }

        @Test
        void shouldHandleEndifWithAttributeName() {
            String content = """
                    = My Title
                    :my-flag:
                    ifdef::my-flag[]
                    :page-included: yes
                    endif::my-flag[]

                    Body.""";

            String result = AsciidocHeaderParser.stripConditionalDirectives(content);
            assertThat(result).doesNotContain("ifdef");
            assertThat(result).doesNotContain("endif");
            assertThat(result).contains(":page-included: yes");
        }
    }
}
