package io.quarkus.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiquidToQuteConverterTest {

    private LiquidToQuteConverter converter;

    @BeforeEach
    void setUp() {
        converter = new LiquidToQuteConverter();
    }

    private void assertConverts(String input, String expected, String message) {
        assertEquals(expected, converter.convert(input), message);
    }

    @Test
    void testEmptyStringSplit() {
        String input = "{=\"\" | split: \",\"}";
        String expected = "{=\"\".split(\",\")}";
        assertConverts(input, expected, "Empty string split should use split method");
    }

    @Test
    void testTernaryWithMethodCall() {
        String input = "{=post.author ?: \"\".split(\",\")}";
        String expected = "{=(post.author ?: \"\").split(\",\")}";
        assertConverts(input, expected, "Ternary before method call should be wrapped in parentheses");
    }

    @Test
    void testTernaryWithTrim() {
        String input = "{=page.author ?: \"\".trim()}";
        String expected = "{=(page.author ?: \"\").trim()}";
        assertConverts(input, expected, "Ternary with trim should be wrapped");
    }

    @Test
    void testSpaceBeforeMethod() {
        String input = "{=variable .trim()}";
        String expected = "{=variable.trim()}";
        assertConverts(input, expected, "Space before method should be removed");
    }

    @Test
    void testStripFilter() {
        String input = "{{text | strip}}";
        String expected = "{=text.trim()}";
        assertConverts(input, expected, "Strip filter should convert to trim()");
    }

    @Test
    void testDefaultFilter() {
        String input = "{{var | default: \"value\"}}";
        String expected = "{=var ?: \"value\"}";
        assertConverts(input, expected, "Default filter should convert to ternary");
    }

    @Test
    void testSplitFilter() {
        String input = "{{text | split: \",\"}}";
        String expected = "{=text.split(\",\")}";
        assertConverts(input, expected, "Split filter should convert to method call");
    }

    @Test
    void testComplexTernaryWithSplit() {
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        // After variable conversion: post.author -> page.author
        // After default filter: page.author ?: ""
        // After split filter: page.author ?: "".split(",")
        // After space removal: page.author ?: "".split(",")
        // After ternary wrapping: (page.author ?: "").split(",")
        String expected = "{=(page.author ?: \"\").split(\",\")}";
        assertConverts(input, expected, "Complex ternary with split should be properly wrapped");
    }

    @Test
    void testVariableConversion() {
        String input = "{{page.title}}";
        String expected = "{=page.title}";
        assertConverts(input, expected, "Variable output should use alternative syntax");
    }

    @Test
    void testPostToPageConversion() {
        String input = "{{post.title}}";
        String expected = "{=page.title}";
        assertConverts(input, expected, "post.* should convert to page.*");
    }

    @Test
    void testIfStatement() {
        String input = "{% if condition %}content{% endif %}";
        String expected = "{#if condition}content{/if}";
        assertConverts(input, expected, "If statement should convert");
    }

    @Test
    void testForLoop() {
        String input = "{% for item in items %}{{item}}{% endfor %}";
        String expected = "{#for item in items}{=item}{/for}";
        assertConverts(input, expected, "For loop should convert");
    }

    @Test
    void testComment() {
        String input = "{% comment %}This is a comment{% endcomment %}";
        String expected = "{! This is a comment !}";
        assertConverts(input, expected, "Comment should convert");
    }

    @Test
    void testDateFilter() {
        String input = "{{page.date | date: \"%Y-%m-%d\"}}";
        String expected = "{=page.date.format('yyyy-MM-dd')}";
        assertConverts(input, expected, "Date filter should convert format");
    }

    @Test
    void testUpcase() {
        String input = "{{text | upcase}}";
        String expected = "{=text.toUpperCase}";
        assertConverts(input, expected, "Upcase filter should convert");
    }

    @Test
    void testDowncase() {
        String input = "{{text | downcase}}";
        String expected = "{=text.toLowerCase}";
        assertConverts(input, expected, "Downcase filter should convert");
    }

    @Test
    void testMultipleFilters() {
        String input = "{{text | strip | upcase}}";
        String expected = "{=text.trim().toUpperCase}";
        assertConverts(input, expected, "Multiple filters should chain");
    }

    @Test
    void testAssignment() {
        String input = "{% assign myvar = \"value\" %}";
        String expected = "{#let myvar=\"value\"}{/let}";
        assertConverts(input, expected, "Assignment should convert");
    }

    @Test
    void testInclude() {
        String input = "{% include \"header.html\" %}";
        String expected = "{#include header.html /}";
        assertConverts(input, expected, "Include should convert");
    }

    @Test
    void testRawBlock() {
        String input = "{% raw %}{{not processed}}{% endraw %}";
        String expected = "{|{{not processed}}|}";
        assertConverts(input, expected, "Raw block content should be preserved verbatim");
    }

    @Test
    void testUnless() {
        String input = "{% unless condition %}content{% endunless %}";
        String expected = "{#if !(condition)}content{/if}";
        assertConverts(input, expected, "Unless should convert to negated if");
    }

    @Test
    void testAndOperator() {
        String input = "{#if a and b}";
        String expected = "{#if a && b}";
        assertConverts(input, expected, "And operator should convert");
    }

    @Test
    void testOrOperator() {
        String input = "{#if a or b}";
        String expected = "{#if a || b}";
        assertConverts(input, expected, "Or operator should convert");
    }

    @Test
    void testRealWorldAuthorExample() {
        // This is the actual pattern from _layouts/author.html that was causing issues
        // Note: post.author is converted to page.author by the converter
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        String expected = "{=(page.author ?: \"\").split(\",\")}";
        assertConverts(input, expected, "Real-world author pattern should convert correctly");
    }

    @Test
    void testMultipleTernariesInSameExpression() {
        String input = "{=a ?: \"\".trim()} and {=b ?: \"\".split(\",\")}";
        String expected = "{=(a ?: \"\").trim()} and {=(b ?: \"\").split(\",\")}";
        assertConverts(input, expected, "Multiple ternaries should all be wrapped");
    }

    @Test
    void testTernaryWithoutMethodCall() {
        String input = "{=var ?: \"default\"}";
        String expected = "{=var ?: \"default\"}";
        assertConverts(input, expected, "Ternary without method call should not be wrapped");
    }

    @Test
    void testAppendFilter() {
        String input = "{{\"hello\" | append: \" world\"}}";
        String expected = "{=\"hello\" + \" world\"}";
        assertConverts(input, expected, "Append filter should convert to concatenation");
    }

    @Test
    void testMultipleAppends() {
        String input = "{{\"a\" | append: \"b\" | append: \"c\"}}";
        String expected = "{=\"a\" + \"b\" + \"c\"}";
        assertConverts(input, expected, "Multiple appends should chain");
    }

    @Test
    void testReplaceFilter() {
        String input = "{{text | replace: 'old', 'new'}}";
        String expected = "{=text.replace('old', 'new')}";
        assertConverts(input, expected, "Replace filter should convert");
    }

    @Test
    void testWhereFilter() {
        String input = "{{array | where: \"key\", \"value\"}}";
        String expected = "{=array.where(\"key\", \"value\")}";
        assertConverts(input, expected, "Where filter should convert");
    }

    @Test
    void testCaseStatement() {
        String input = "{% case var %}{% when val1 %}a{% endcase %}";
        String expected = "{#if var == val1}a{/if}";
        assertConverts(input, expected, "Case statement should convert to if");
    }

    @Test
    void testElsif() {
        String input = "{% if a %}1{% elsif b %}2{% else %}3{% endif %}";
        String expected = "{#if a}1{#else if b}2{#else}3{/if}";
        assertConverts(input, expected, "Elsif should convert");
    }

    @Test
    void testCapture() {
        String input = "{% capture myvar %}content{% endcapture %}";
        String expected = "{#let myvar}content{/let}";
        assertConverts(input, expected, "Capture should convert");
    }

    @Test
    void testAssignWithEmptyStringSplit() {
        String input = "{% assign authors_clean = \"\" | split: \"\" %}";
        String expected = "{#let authors_clean=\"\".split(\"\")}{/let}";
        assertConverts(input, expected, "Empty string split in assignment should produce valid Qute");
    }

    @Test
    void testFilterNotConvertedOutsideBlocks() {
        String input = "\"\" | split: \"\"";
        String expected = "\"\" | split: \"\"";
        assertConverts(input, expected,
                "Filters outside expression blocks should not be converted");
    }

    @Test
    void testAssignWithPushFilter() {
        String input = "{% assign authors_clean = authors_clean | push: a_trimmed %}";
        String expected = "{#let authors_clean=authors_clean.push(a_trimmed)}{/let}";
        assertConverts(input, expected, "Assignment with push filter should convert correctly");
    }

    @Test
    void testAndOrInProseNotCorrupted() {
        String input = "<p>This is information or data and more text</p>\n" +
                       "{% if a and b %}yes{% endif %}";
        String expected = "<p>This is information or data and more text</p>\n" +
                         "{#if a && b}yes{/if}";
        assertConverts(input, expected,
                "and/or in prose text should not be converted, only inside conditionals");
    }

    @Test
    void testAuthorFileLines36to38() {
        String input = "      {% comment %} Build multi-author list for this post {% endcomment %}\n" +
                       "      {% assign authors_raw = post.author | default: \"\" | split: \",\" %}\n" +
                       "      {% assign authors_clean = \"\" | split: \"\" %}";

        String expected = "      {!  Build multi-author list for this post  !}\n" +
                         "      {#let authors_raw=(page.author ?: \"\").split(\",\")}\n" +
                         "      {#let authors_clean=\"\".split(\"\")}{/let}{/let}";

        assertConverts(input, expected, "Author file lines 36-38 should convert without {?:} errors");
    }

    @Test
    void testWhitespaceTrimmingTags() {
        // Liquid: {%- if ... -%} and {% endif -%} (whitespace trimming)
        String input = "{%- if condition -%}content{%- endif -%}";
        String expected = "{#if condition}content{/if}";
        assertConverts(input, expected,
                "Whitespace-trimming tags should be handled like normal tags");
    }

    @Test
    void testWhitespaceTrimmingEndFor() {
        String input = "{% for item in items %}{=item}{% endfor -%}";
        String expected = "{#for item in items}{=item}{/for}";
        assertConverts(input, expected,
                "endfor with whitespace trimming should convert");
    }

    @Test
    void testReplaceRegexFilter() {
        String input = "{{page.url | replace_regex: '^/version/([^/]+)/.*', '\\1'}}";
        String expected = "{=page.url.replaceAll('^/version/([^/]+)/.*', '\\1')}";
        assertConverts(input, expected,
                "replace_regex filter should convert to .replaceAll() method call");
    }

    @Test
    void testStartsWithFilter() {
        String input = "{% assign versioned = page.url | startswith: '/version/' %}";
        String expected = "{#let versioned=page.url.startsWith('/version/')}{/let}";
        assertConverts(input, expected,
                "startswith filter should convert to .startsWith() method call");
    }

    @Test
    void testEndsWithFilter() {
        String input = "{{title | endswith: 'Quarkus'}}";
        String expected = "{=title.endsWith('Quarkus')}";
        assertConverts(input, expected,
                "endswith filter should convert to .endsWith() method call");
    }

    @Test
    void testSortFilterWithArgument() {
        String input = "{% assign authors = site.data.authors | sort: name %}";
        String expected = "{#let authors=site.data.authors.sort(name)}{/let}";
        assertConverts(input, expected,
                "Sort filter with argument should convert to method call with argument");
    }

    @Test
    void testPrependFilter() {
        // Liquid: {{ path | prepend: site.baseurl }}
        // Qute: site.baseurl + path (prepend = concatenate before)
        String input = "{{paginator.next_page_path | prepend: site.baseurl}}";
        String expected = "{=site.baseurl + paginator.next_page_path}";
        assertConverts(input, expected,
                "Prepend filter should convert to string concatenation");
    }

    @Test
    void testDynamicBracketNotation() {
        String input = "{% assign author = site.data.authors[author_key] %}";
        String expected = "{#let author=site.data.authors.get(author_key)}{/let}";
        assertConverts(input, expected,
                "Dynamic bracket notation should be converted to .get() method call");
    }

    @Test
    void testDynamicBracketNotationInVariable() {
        String input = "{{ site.data.authors[key].name }}";
        String expected = "{=site.data.authors.get(key).name}";
        assertConverts(input, expected,
                "Bracket notation followed by property access should convert correctly");
    }

    // --- Loop variable tests ---

    @Test
    void testForLoopIndexWithNamedVar() {
        String input = "{% for post in posts %}{{forloop.index0}} {{forloop.index}}{% endfor %}";
        String expected = "{#for post in posts}{=post_index} {=post_count}{/for}";
        assertConverts(input, expected,
                "forloop.index0/index should use the loop variable name");
    }

    @Test
    void testForLoopFirstUsesCount() {
        String input = "{% for item in items %}{% if forloop.first %}first{% endif %}{% endfor %}";
        String expected = "{#for item in items}{#if item_count == 1}first{/if}{/for}";
        assertConverts(input, expected,
                "forloop.first should convert to var_count == 1");
    }

    @Test
    void testForLoopLastUsesHasNext() {
        String input = "{% for entry in entries %}{% if forloop.last %}last{% endif %}{% endfor %}";
        String expected = "{#for entry in entries}{#if !entry_hasNext}last{/if}{/for}";
        assertConverts(input, expected,
                "forloop.last should convert to !var_hasNext");
    }

    @Test
    void testNestedForLoopsUseDifferentVarNames() {
        String input = "{% for cat in categories %}{% for post in cat.posts %}{{forloop.index}}{% endfor %}{% endfor %}";
        String expected = "{#for cat in categories}{#for post in cat.posts}{=post_count}{/for}{/for}";
        assertConverts(input, expected,
                "Nested loops should use their own variable name for metadata");
    }

    @Test
    void testForLoopWithLimitAndOffset() {
        String input = "{% for item in items limit:3 offset:2 %}{{item}}{% endfor %}";
        String expected = "{#for item in items.skip(2).limit(3)}{=item}{/for}";
        assertConverts(input, expected,
                "Loop with limit and offset should convert to .skip().limit()");
    }

    // --- Layout tag tests ---

    @Test
    void testLayoutInheritance() {
        String input = "{% layout \"default\" %}";
        String expected = "{! TODO: set layout in front matter instead: layout: default !}";
        assertConverts(input, expected, "Layout tag should emit front matter TODO");
    }

    @Test
    void testBlockTags() {
        String input = "{% block content %}body{% endblock %}";
        String expected = "{#block content}body{/block}";
        assertConverts(input, expected, "Block tags should convert");
    }

    // --- Highlight block tests ---

    @Test
    void testHighlightBlock() {
        String input = "{% highlight java %}System.out.println();{% endhighlight %}";
        String expected = "<pre><code class=\"language-java\">System.out.println();</code></pre>";
        assertConverts(input, expected, "Highlight blocks should convert to pre/code");
    }

    // --- Full pipeline and/or tests ---

    @Test
    void testFullPipelineAndOrInConditional() {
        String input = "{% if a and b or c %}yes{% endif %}";
        String expected = "{#if a && b || c}yes{/if}";
        assertConverts(input, expected,
                "and/or in raw Liquid conditionals should convert through the full pipeline");
    }

    // --- Prose safety tests ---

    @Test
    void testSpaceBeforeMethodPreservedInProse() {
        String input = "<p>See docs .method for details</p>";
        String expected = "<p>See docs .method for details</p>";
        assertConverts(input, expected,
                "Spaces before dot-words in prose should not be removed");
    }

    @Test
    void testSpaceBeforeMethodRemovedInExpression() {
        String input = "{=variable .trim()}";
        String expected = "{=variable.trim()}";
        assertConverts(input, expected,
                "Spaces before method calls inside expressions should be removed");
    }

    // --- No-change reporting tests ---

    @Test
    void testNoChangeReportsNoConversions() {
        String input = "<p>Plain HTML with no Liquid</p>";
        converter.convert(input);
        assertEquals("No conversions needed", converter.getConversionReport(),
                "Content with no Liquid should report no conversions");
    }

    // --- Regression tests for bug fixes ---

    @Test
    void testAndOrInsideStringNotCorrupted() {
        String input = "{% if label == \"and\" %}yes{% endif %}";
        String expected = "{#if label == \"and\"}yes{/if}";
        assertConverts(input, expected,
                "and/or inside string literals in conditions should not be replaced");
    }

    @Test
    void testOrInsideStringNotCorrupted() {
        String input = "{% if type == 'or' and active %}yes{% endif %}";
        String expected = "{#if type == 'or' && active}yes{/if}";
        assertConverts(input, expected,
                "or inside quotes preserved, and outside quotes converted");
    }

    @Test
    void testFilterNotConvertedInMarkdownTable() {
        String input = "| Name | Size | Status |\n| sort | reverse | done |";
        String expected = "| Name | Size | Status |\n| sort | reverse | done |";
        assertConverts(input, expected,
                "Pipes and filter-like words in markdown tables should not be converted");
    }

    @Test
    void testCaseStatementWithMultipleWhens() {
        String input = "{% case status %}{% when \"active\" %}A{% when \"inactive\" %}I{% when \"pending\" %}P{% endcase %}";
        String expected = "{#if status == \"active\"}A{#else if status == \"inactive\"}I{#else if status == \"pending\"}P{/if}";
        assertConverts(input, expected,
                "Case with multiple whens should produce if/else if chain");
    }

    @Test
    void testDateFilterWith12Hour() {
        String input = "{{page.date | date: \"%I:%M %p\"}}";
        String expected = "{=page.date.format('hh:mm a')}";
        assertConverts(input, expected, "12-hour date format should convert");
    }

    @Test
    void testDateFilterUnknownSpecifier() {
        String input = "{{page.date | date: \"%Y-%Q\"}}";
        String expected = "{=page.date.format('yyyy-%Q /* TODO: unsupported strftime specifiers */')}";
        assertConverts(input, expected, "Unknown date specifier should emit TODO");
    }

    @Test
    void testRawBlockPreservesLiquidTags() {
        String input = "{% raw %}{% if x %}hello{% endif %}{% endraw %}";
        String expected = "{|{% if x %}hello{% endif %}|}";
        assertConverts(input, expected, "Raw block should preserve Liquid tags verbatim");
    }

    // --- Assign scope boundary tests ---

    @Test
    void testAssignScopeExtendsToEndOfContent() {
        String input = "{% assign x = \"hello\" %}\n{=x}\nmore content";
        String expected = "{#let x=\"hello\"}\n{=x}\nmore content{/let}";
        assertConverts(input, expected,
                "Assign at top level should scope to end of content");
    }

    @Test
    void testAssignScopeEndsAtEnclosingForLoop() {
        String input = "{#for item in items}{% assign x = item.name %}{=x}{/for}after";
        String expected = "{#for item in items}{#let x=item.name}{=x}{/let}{/for}after";
        assertConverts(input, expected,
                "Assign inside a for loop should scope to the loop's end");
    }

    @Test
    void testAssignScopeEndsAtEnclosingIf() {
        String input = "{#if cond}{% assign x = \"val\" %}{=x}{/if}";
        String expected = "{#if cond}{#let x=\"val\"}{=x}{/let}{/if}";
        assertConverts(input, expected,
                "Assign inside an if block should scope to the if's end");
    }

    @Test
    void testMultipleAssignsNestCorrectly() {
        String input = "{% assign a = 1 %}\n{% assign b = 2 %}\n{=a} {=b}";
        String expected = "{#let a=1}\n{#let b=2}\n{=a} {=b}{/let}{/let}";
        assertConverts(input, expected,
                "Multiple top-level assigns should nest with both {/let}s at end");
    }

    @Test
    void testAssignInIfBranchScopedBeforeElse() {
        String input = "{% if page.title %}{% assign x = page.title %}{% else %}{% assign x = 'default' %}{% endif %}";
        String expected = "{#if page.title}{#let x=page.title}{/let}{#else}{#let x='default'}{/let}{/if}";
        assertConverts(input, expected,
                "Assign in if branch should be scoped before the else, not crossing into it");
    }

    @Test
    void testChainedFiltersRemoveSpaces() {
        String input = "{{page.content | strip_html | truncatewords: 75}}";
        String expected = "{=page.content.stripHtml.wordLimit(75)}";
        assertConverts(input, expected,
                "Chained filters should not have spaces between method calls");
    }

    @Nested
    class StandardSyntaxTest {

        private LiquidToQuteConverter converter;

        @BeforeEach
        void setUp() {
            converter = new LiquidToQuteConverter(false);
        }

        private void assertConverts(String input, String expected, String message) {
            assertEquals(expected, converter.convert(input), message);
        }

        @Test
        void testVariable() {
            assertConverts("{{page.title}}", "{page.title}",
                    "Standard syntax should use {expr} not {=expr}");
        }

        @Test
        void testFilter() {
            assertConverts("{{text | upcase}}", "{text.toUpperCase}",
                    "Standard syntax should apply filters inside {expr}");
        }

        @Test
        void testDefaultFilter() {
            assertConverts("{{var | default: \"value\"}}", "{var ?: \"value\"}",
                    "Standard syntax should convert default filter inside {expr}");
        }

        @Test
        void testChainedFilters() {
            assertConverts("{{page.content | strip_html | truncatewords: 75}}",
                    "{page.content.stripHtml.wordLimit(75)}",
                    "Standard syntax should chain filters correctly");
        }

        @Test
        void testTernaryWrapping() {
            assertConverts("{{post.author | default: \"\" | split: \",\"}}",
                    "{(page.author ?: \"\").split(\",\")}",
                    "Standard syntax should wrap ternary before method calls");
        }

        @Test
        void testForLoop() {
            assertConverts("{% for item in items %}{{item}}{% endfor %}",
                    "{#for item in items}{item}{/for}",
                    "Standard syntax for loop should use {expr} for outputs");
        }

        @Test
        void testSpaceRemoval() {
            assertConverts("{variable .trim()}", "{variable.trim()}",
                    "Standard syntax should remove spaces before methods in expressions");
        }

        @Test
        void testExtensionSyntaxDefaultConstructor() {
            LiquidToQuteConverter ext = new LiquidToQuteConverter();
            assertEquals("{=page.title}", ext.convert("{{page.title}}"),
                    "Default constructor should use extension syntax");
        }
    }
}
