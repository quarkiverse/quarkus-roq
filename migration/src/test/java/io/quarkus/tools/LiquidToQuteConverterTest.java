package io.quarkus.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        String expected = "{=str:split(\"\", \",\")}";
        assertConverts(input, expected, "Empty string split should use namespace split");
    }

    @Test
    void testTernaryWithMethodCall() {
        String input = "{=post.data.author ?: \"\".split(\",\")}";
        String expected = "{=(post.data.author ?: \"\").split(\",\")}";
        assertConverts(input, expected, "Ternary before method call should be wrapped in parentheses");
    }

    @Test
    void testTernaryWithTrim() {
        String input = "{=page.data.author ?: \"\".trim()}";
        String expected = "{=(page.data.author ?: \"\").trim()}";
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
        String expected = "{=str:split(text, \",\")}";
        assertConverts(input, expected, "Split filter should use namespace form");
    }

    @Test
    void testDefaultBeforeSplitStripsDefault() {
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        // default is stripped because namespace split handles null
        String expected = "{=str:split(post.data.author??, \",\")}";
        assertConverts(input, expected, "default before split should be stripped; split uses namespace form");
    }

    @Test
    void testVariableConversion() {
        String input = "{{page.title}}";
        String expected = "{=page.title}";
        assertConverts(input, expected, "Variable output should use alternative syntax");
    }

    @Test
    void testPostKeepsPostPrefix() {
        String input = "{{post.title}}";
        String expected = "{=post.title}";
        assertConverts(input, expected, "post.* should stay as post.* (may be a loop variable)");
    }

    @Test
    void testIfStatement() {
        String input = "{% if condition %}content{% endif %}";
        String expected = "{#if condition}content{/if}";
        assertConverts(input, expected, "If statement should convert");
    }

    @Test
    void testPaginatorPostsConverted() {
        String input = "{% for post in paginator.posts %}{{post.title}}{% endfor %}";
        String expected = "{#for post in site.collections.get('posts').paginated(page.paginator).orEmpty}{=post.title}{/for}";
        assertConverts(input, expected,
                "paginator.posts should convert to Roq collection access");
    }

    @Test
    void testPaginatorTotalPages() {
        String input = "{% if paginator.total_pages > 1 %}yes{% endif %}";
        String expected = "{#if page.paginator && page.paginator.total > 1}yes{/if}";
        assertConverts(input, expected,
                "paginator.total_pages should convert to page.paginator.total with null guard");
    }

    @Test
    void testPaginatorNextPage() {
        String input = "{% if paginator.next_page %}yes{% endif %}";
        String expected = "{#if page.paginator && page.paginator.next}yes{/if}";
        assertConverts(input, expected,
                "paginator.next_page should convert to page.paginator.next");
    }

    @Test
    void testPaginatorPreviousPage() {
        String input = "{% if paginator.previous_page %}yes{% endif %}";
        String expected = "{#if page.paginator && page.paginator.previous}yes{/if}";
        assertConverts(input, expected,
                "paginator.previous_page should convert to page.paginator.previous");
    }

    @Test
    void testPaginatorNextPagePath() {
        String input = "{{paginator.next_page_path}}";
        String expected = "{=page.paginator.next}";
        assertConverts(input, expected,
                "paginator.next_page_path should convert to page.paginator.next");
    }

    @Test
    void testPaginatorPreviousPagePath() {
        String input = "{{paginator.previous_page_path}}";
        String expected = "{=page.paginator.previous}";
        assertConverts(input, expected,
                "paginator.previous_page_path should convert to page.paginator.previous");
    }

    @Test
    void testForLoop() {
        String input = "{% for item in items %}{{item}}{% endfor %}";
        String expected = "{#for item in items.orEmpty}{=item}{/for}";
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
        String expected = "{#include partials/header.html /}";
        assertConverts(input, expected, "Include should convert with partials/ prefix");
    }

    @Test
    void testIncludeParamAccess() {
        String input = "{=include.page_title}";
        String expected = "{=page_title}";
        assertConverts(input, expected,
                "include.param should convert to just param (Qute exposes include params directly)");
    }

    @Test
    void testIncludeParamInConditional() {
        String input = "{#if include.page_title}yes{/if}";
        String expected = "{#if page_title}yes{/if}";
        assertConverts(input, expected,
                "include.param in conditionals should also be stripped");
    }

    @Test
    void testIncludeParamInAssignment() {
        String input = "{% assign x = include.url %}";
        String expected = "{#let x=url}{/let}";
        assertConverts(input, expected,
                "include.param in assignments should be stripped");
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
        String expected = "{#if !condition}content{/if}";
        assertConverts(input, expected, "Unless should convert to negated if");
    }

    @Test
    void testUnlessWithOr() {
        String input = "{% unless item.completed or item.lts or item.status != 'on track' %}content{% endunless %}";
        String expected = "{#if !item.completed && !item.lts && item.status == 'on track'}content{/if}";
        assertConverts(input, expected, "Unless with or should apply De Morgan's law");
    }

    @Test
    void testUnlessWithComparison() {
        String input = "{% unless item.status == 'active' %}content{% endunless %}";
        String expected = "{#if item.status ne 'active'}content{/if}";
        assertConverts(input, expected, "Unless with == should negate to ne");
    }

    @Test
    void testUnlessForloopLast() {
        String input = "{% for x in items %}{% unless forloop.last %}, {% endunless %}{% endfor %}";
        String expected = "{#for x in items.orEmpty}{#if x_hasNext}, {/if}{/for}";
        assertConverts(input, expected, "Unless forloop.last should become if hasNext (no double negation)");
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
        // This is the actual pattern from _layouts/author.html
        // post.author is custom frontmatter -> post.data.author
        // default is stripped; split uses namespace form for null safety
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        String expected = "{=str:split(post.data.author??, \",\")}";
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
        String expected = "{=text.replace('old', 'new').raw}";
        assertConverts(input, expected, "Replace filter should convert with .raw (Jekyll never escapes)");
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
        String expected = "{#let myvar=''}content{/let}";
        assertConverts(input, expected,
                "Capture must produce valid {#let} with empty initial value — bare {#let myvar} is a Qute parse error");
    }

    @Test
    void testAssignWithEmptyStringSplit() {
        String input = "{% assign authors_clean = \"\" | split: \"\" %}";
        String expected = "{#let authors_clean=str:split(\"\", \"\")}{/let}";
        assertConverts(input, expected, "Empty string split in assignment should use namespace form");
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
    void testPushInLoopCollapsedToSplitTrimmed() {
        String input = "{% assign authors_raw = post.author | default: \"\" | split: \",\" %}\n" +
                "{% assign authors_clean = \"\" | split: \"\" %}\n" +
                "{% for a in authors_raw %}\n" +
                "{% assign a_trimmed = a | strip %}\n" +
                "{% if a_trimmed != \"\" %}\n" +
                "{% assign authors_clean = authors_clean | push: a_trimmed %}\n" +
                "{% endif %}\n" +
                "{% endfor %}\n" +
                "{% for author_key in authors_clean %}\n" +
                "{{author_key}}\n" +
                "{% endfor %}";
        String expected = "{#for author_key in str:splitTrimmed(post.data.author??, \",\").orEmpty}\n" +
                "{=author_key}\n" +
                "{/for}";
        assertConverts(input, expected,
                "Init-empty-list + push-in-loop + iterate should collapse to str:splitTrimmed");
    }

    @Test
    void testPushInLoopWithHtmlBetween() {
        String input = "{% assign authors_raw = post.author | default: \"\" | split: \",\" %}\n" +
                "{% assign authors_clean = \"\" | split: \"\" %}\n" +
                "{% for a in authors_raw %}\n" +
                "{% assign a_trimmed = a | strip %}\n" +
                "{% if a_trimmed != \"\" %}\n" +
                "{% assign authors_clean = authors_clean | push: a_trimmed %}\n" +
                "{% endif %}\n" +
                "{% endfor %}\n" +
                "<p class=\"byline\">\n" +
                "By\n" +
                "{% for author_key in authors_clean %}\n" +
                "{{author_key}}\n" +
                "{% endfor %}";
        String expected = "<p class=\"byline\">\n" +
                "By\n" +
                "{#for author_key in str:splitTrimmed(post.data.author??, \",\").orEmpty}\n" +
                "{=author_key}\n" +
                "{/for}";
        assertConverts(input, expected,
                "Push-in-loop with HTML between should collapse, preserving the HTML");
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
                "      {#let authors_raw=str:split(post.data.author??, \",\")}\n" +
                "      {#let authors_clean=str:split(\"\", \"\")}{/let}{/let}";

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
        String expected = "{#for item in items.orEmpty}{=item}{/for}";
        assertConverts(input, expected,
                "endfor with whitespace trimming should convert");
    }

    @Test
    void testReplaceRegexFilter() {
        String input = "{{page.url | replace_regex: '^/version/([^/]+)/.*', '\\1'}}";
        String expected = "{=page.url.replaceAll('^/version/([^/]+)/.*', '$1')}";
        assertConverts(input, expected,
                "replace_regex filter should convert to .replaceAll() with Java backreference syntax");
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
        String expected = "{#let authors=cdi:authors.sort(name)}{/let}";
        assertConverts(input, expected,
                "Sort filter with argument should convert to method call with argument");
    }

    @Test
    void testPrependFilter() {
        // Liquid: {{ path | prepend: site.baseurl }}
        // Qute: cdi:siteConfig.baseurl + path (prepend = concatenate before, siteConfig from data/siteConfig.yml)
        String input = "{{paginator.next_page_path | prepend: site.baseurl}}";
        String expected = "{=cdi:siteConfig.baseurl + page.paginator.next}";
        assertConverts(input, expected,
                "Prepend filter should convert to string concatenation with CDI reference for site.baseurl");
    }

    @Test
    void testDynamicBracketNotation() {
        String input = "{% assign author = site.data.authors[author_key] %}";
        String expected = "{#let author=cdi:authors.get(author_key)}{/let}";
        assertConverts(input, expected,
                "Dynamic bracket notation should be converted to .get() method call");
    }

    @Test
    void testDynamicBracketNotationInVariable() {
        String input = "{{ site.data.authors[key].name }}";
        String expected = "{=cdi:authors.get(key).name}";
        assertConverts(input, expected,
                "Bracket notation followed by property access should convert correctly");
    }

    // --- Loop variable tests ---

    @Test
    void testForLoopIndexWithNamedVar() {
        String input = "{% for post in posts %}{{forloop.index0}} {{forloop.index}}{% endfor %}";
        String expected = "{#for post in posts.orEmpty}{=post_index} {=post_count}{/for}";
        assertConverts(input, expected,
                "forloop.index0/index should use the loop variable name");
    }

    @Test
    void testForLoopFirstUsesCount() {
        String input = "{% for item in items %}{% if forloop.first %}first{% endif %}{% endfor %}";
        String expected = "{#for item in items.orEmpty}{#if item_count == 1}first{/if}{/for}";
        assertConverts(input, expected,
                "forloop.first should convert to var_count == 1");
    }

    @Test
    void testForLoopLastUsesHasNext() {
        String input = "{% for entry in entries %}{% if forloop.last %}last{% endif %}{% endfor %}";
        String expected = "{#for entry in entries.orEmpty}{#if !entry_hasNext}last{/if}{/for}";
        assertConverts(input, expected,
                "forloop.last should convert to !var_hasNext");
    }

    @Test
    void testNestedForLoopsUseDifferentVarNames() {
        String input = "{% for cat in categories %}{% for post in cat.posts %}{{forloop.index}}{% endfor %}{% endfor %}";
        String expected = "{#for cat in categories.orEmpty}{#for post in cat.posts.orEmpty}{=post_count}{/for}{/for}";
        assertConverts(input, expected,
                "Nested loops should use their own variable name for metadata");
    }

    @Test
    void testForLoopWithLimitAndOffset() {
        String input = "{% for item in items limit:3 offset:2 %}{{item}}{% endfor %}";
        String expected = "{#for item in items.orEmpty.skip(2).limit(3)}{=item}{/for}";
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
        String input = "{#for item in items.orEmpty}{% assign x = item.name %}{=x}{/for}after";
        String expected = "{#for item in items.orEmpty}{#let x=item.name}{=x}{/let}{/for}after";
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
    void testAssignInIfElseBranchesWithFalseElse() {
        String input = "{% if page.title %}{% assign x = page.title %}{% else %}{% assign x = false %}{% endif %}";
        // condition == ifExpr, so Elvis applies: page.title ?: false
        String expected = "{#let x=page.title ?: false}\n{/let}";
        assertConverts(input, expected,
                "Same variable in if/else with false else should convert to Elvis");
    }

    @Test
    void testAssignInIfElseBranchesElvisCase() {
        String input = "{% if page.title %}{% assign x = page.title %}{% else %}{% assign x = 'default' %}{% endif %}";
        String expected = "{#let x=page.title ?: 'default'}\n{/let}";
        assertConverts(input, expected,
                "Same variable in if/else where condition matches if-value should use Elvis operator");
    }

    @Test
    void testReplaceRegexWithPrependChain() {
        String input = "{% assign x = page.url | replace_regex: '^/version/([^/]+)/.*', '\\1' | prepend: ' - ' %}";
        String expected = "{#let x=' - ' + page.url.replaceAll('^/version/([^/]+)/.*', '$1')}{/let}";
        assertConverts(input, expected,
                "replace_regex chained with prepend should produce valid method call");
    }

    @Test
    void testChainedFiltersRemoveSpaces() {
        String input = "{{page.content | strip_html | truncatewords: 75}}";
        String expected = "{=page.content.stripHtml.wordLimit(75)}";
        assertConverts(input, expected,
                "Chained filters should not have spaces between method calls");
    }

    @Test
    void testPostCustomFieldConvertToData() {
        String input = "{{post.author}}";
        String expected = "{=post.data.author??}";
        assertConverts(input, expected,
                "post.customField should convert to post.data.customField (DocumentPage custom frontmatter)");
    }

    @Test
    void testPostBuiltInFieldsNotConverted() {
        String input = "{{post.title}} {{post.url}} {{post.date}} {{post.content}}";
        String expected = "{=post.title} {=post.url} {=post.date} {=post.content}";
        assertConverts(input, expected,
                "post built-in properties should not be converted to post.data.*");
    }

    @Test
    void testPostSynopsisConverted() {
        String input = "{#if post.synopsis}yes{/if}";
        String expected = "{#if post.data.synopsis??}yes{/if}";
        assertConverts(input, expected,
                "post.synopsis should convert to post.data.synopsis");
    }

    @Test
    void testIncludeWithLeadingSlashStripped() {
        String input = "{% include /templates/secondary-page-title-band.html %}";
        String expected = "{#include partials/templates/secondary-page-title-band.html /}";
        assertConverts(input, expected,
                "Leading slash in include path should be stripped to avoid double-slash in partials/ prefix");
    }

    @Test
    void testIncludePathWithPageNotMangled() {
        String input = "{% include share-page.html title=post.title url=post.url %}";
        String expected = "{#include partials/share-page.html title=post.title url=post.url /}";
        assertConverts(input, expected,
                "Include paths containing '-page.' should not be treated as page field access");
    }

    @Test
    void testSiteBaseurlConvertsToCdi() {
        String input = "<a href=\"{{site.baseurl}}/path\">Link</a>";
        String expected = "<a href=\"{=cdi:siteConfig.baseurl}/path\">Link</a>";
        assertConverts(input, expected,
                "site.baseurl should convert to CDI reference");
    }

    @Test
    void testSiteLanguageConvertsToCdi() {
        String input = "<html lang=\"{{site.language}}\">";
        String expected = "<html lang=\"{=cdi:siteConfig.language}\">";
        assertConverts(input, expected,
                "site.language should convert to CDI reference");
    }

    @Test
    void testSiteBaseurlInConditional() {
        String input = "{% if site.baseurl %}<base href=\"{{site.baseurl}}\">{% endif %}";
        String expected = "{#if cdi:siteConfig.baseurl}<base href=\"{=cdi:siteConfig.baseurl}\">{/if}";
        assertConverts(input, expected,
                "site.baseurl in conditionals should convert to CDI reference");
    }

    @Test
    void testSiteCustomPropertyConvertsToCdi() {
        String input = "{=site.twitter_username}";
        String expected = "{=cdi:siteConfig.twitter_username}";
        assertConverts(input, expected,
                "Custom site properties should convert to cdi:siteConfig references");
    }

    @Test
    void testSiteBuiltInPropertiesNotConverted() {
        String input = "{=site.url} {=site.title} {=site.collections} {=site.pages} {=site.data}";
        String expected = "{=site.url} {=site.title} {=site.collections} {=site.pages} {=site.data}";
        assertConverts(input, expected,
                "Roq Site built-in properties should not be converted to cdi:siteConfig");
    }

    @Test
    void testCustomPageFieldConvertToData() {
        String input = "{{page.data.author}}";
        String expected = "{=page.data.author??}";
        assertConverts(input, expected,
                "Custom page frontmatter fields should be lenient (may not exist)");
    }

    @Test
    void testBuiltInPageFieldsNotConverted() {
        String input = "{{page.title}} {{page.date}} {{page.url}}";
        String expected = "{=page.title} {=page.date} {=page.url}";
        assertConverts(input, expected,
                "Built-in page properties should not be converted to page.data.*");
    }

    @Test
    void testMultipleCustomPageFields() {
        String input = "{{page.data.author}} - {{page.synopsis}}";
        String expected = "{=page.data.author??} - {=page.data.synopsis??}";
        assertConverts(input, expected,
                "Multiple custom fields should all convert to page.data.* with lenient operator");
    }

    @Test
    void testCustomFieldInConditional() {
        String input = "{% if page.search_wc %}...{% endif %}";
        String expected = "{#if page.data.search_wc??}...{/if}";
        assertConverts(input, expected,
                "Custom fields in conditionals should convert to page.data.* with lenient operator");
    }

    @Test
    void testSiteSearchConvertsToCdi() {
        String input = "{{site.search.host}}";
        String expected = "{=cdi:siteConfig.search.host}";
        assertConverts(input, expected,
                "site.search properties should convert to CDI reference");
    }

    @Test
    void testSiteSearchScriptMode() {
        String input = "{% if site.search.script-mode == 'direct' %}...{% endif %}";
        String expected = "{#if cdi:siteConfig.search.script-mode == 'direct'}...{/if}";
        assertConverts(input, expected,
                "site.search.script-mode should convert to CDI reference");
    }

    @Test
    void testUrlConcatenationWithSiteUrl() {
        String input = "{{site.url | append: page.url}}";
        String expected = "{=site.url.resolve(page.url)}";
        assertConverts(input, expected,
                "URL concatenation should use .resolve() instead of +");
    }

    @Test
    void testUrlConcatenationWithVariable() {
        String input = "{{canonical_url | prepend: site.url}}";
        String expected = "{=site.url.resolve(canonical_url)}";
        assertConverts(input, expected,
                "Prepending to URL should use .resolve()");
    }

    @Test
    void testPageUrlEqualityComparison() {
        String input = "{#if page.url == '/'}homepage{#else}{=page.data.layout}{/if}";
        String expected = "{#if page.url.path == '/'}homepage{#else}{=page.data.layout??}{/if}";
        assertConverts(input, expected,
                "page.url == should convert to page.url.path == (RoqUrl is not a String)");
    }

    @Test
    void testPageUrlNotEqualsComparison() {
        String input = "{#if page.url != '/about/'}other{/if}";
        String expected = "{#if page.url.path ne '/about/'}other{/if}";
        assertConverts(input, expected,
                "page.url != should convert to page.url.path ne");
    }

    @Test
    void testSiteDataToCdiReference() {
        assertConverts("{{ site.data.projectfooter.links }}", "{=cdi:projectfooter.links}",
                "site.data.X should convert to cdi:X");
    }

    @Test
    void testSiteDataNestedReference() {
        assertConverts("{{ site.data.versions.documentation }}",
                "{=cdi:versions.documentation}",
                "site.data.X.Y should convert to cdi:X.Y");
    }

    @Test
    void testContainsInIfCondition() {
        assertConverts("{% if page.url contains '/guides/' %}yes{% endif %}",
                "{#if page.url.contains('/guides/')}yes{/if}",
                "contains operator in if should convert to method call");
    }

    @Test
    void testContainsInIfConditionWithAnd() {
        assertConverts("{% if page.url contains '/guides/' and page.title %}yes{% endif %}",
                "{#if page.url.contains('/guides/') && page.title}yes{/if}",
                "contains with and should convert both operators");
    }

    @Test
    void testAssignInIfElseBlockGeneralCaseInlinesTrailingContent() {
        String input = """
                {% if page.layout == 'guides' %}
                  {%assign canonical_url = page.url | replace: 'foo', '' %}
                {% else %}
                  {%assign canonical_url = page.url %}
                {% endif %}
                <link rel="canonical" href="{{ canonical_url }}">
                """;
        String result = converter.convert(input);

        // General case: trailing content using the variable is duplicated into each branch
        assertTrue(result.contains("{#if"), "If/else should be preserved");
        // The <link> line should appear twice (once per branch)
        int firstLink = result.indexOf("canonical");
        int secondLink = result.indexOf("canonical", firstLink + 1);
        assertTrue(secondLink > firstLink, "Trailing content should be duplicated into both branches");
    }

    @Test
    void testRelativeUrlFilter() {
        String input = "{{ '/assets/javascript/highlight.pack.js' | relative_url }}";
        String expected = "{=cdi:siteConfig.baseurl + '/assets/javascript/highlight.pack.js'}";
        assertConverts(input, expected, "relative_url filter should prepend baseurl");
    }

    @Test
    void testRelativeUrlFilterWithVariable() {
        String input = "{{ page.url | relative_url }}";
        String expected = "{=cdi:siteConfig.baseurl + page.url}";
        assertConverts(input, expected, "relative_url filter on variable should prepend baseurl");
    }

    @Test
    void testAbsoluteUrlFilter() {
        String input = "{{ '/feed.xml' | absolute_url }}";
        String expected = "{=cdi:siteConfig.baseurl + '/feed.xml'}";
        assertConverts(input, expected, "absolute_url filter should prepend baseurl");
    }

    @Test
    void testPrependWithStringLiteral() {
        String input = "{{ '/assets/images/quarkus_card.png' | prepend: site.url }}";
        String expected = "{=site.url.resolve('/assets/images/quarkus_card.png')}";
        assertConverts(input, expected, "prepend with string literal containing slashes should work");
    }

    @Test
    void testNotEqualsConvertsToNe() {
        String input = "{% if site.cname != 'quarkus.io' %}content{% endif %}";
        String expected = "{#if cdi:siteConfig.cname ne 'quarkus.io'}content{/if}";
        assertConverts(input, expected, "!= should convert to ne in if conditions");
    }

    @Test
    void testContentVariableInLayout() {
        String input = "<div>{{ content }}</div>";
        String expected = "<div>{#insert /}</div>";
        assertConverts(input, expected, "{{ content }} in layout should become {#insert /}");
    }

    @Test
    void testContentVariableInPartial() {
        LiquidToQuteConverter partialConverter = new LiquidToQuteConverter();
        partialConverter.setConvertingPartials(true);
        String input = "<div>{{ content }}</div>";
        String expected = "<div>{=page.content}</div>";
        assertEquals(expected, partialConverter.convert(input),
                "{{ content }} in partial should become {=page.content} to avoid infinite recursion");
    }

    @Test
    void testSitePostsConversion() {
        String input = "{% for post in site.posts %}text{% endfor %}";
        String expected = "{#for post in site.collections.get('posts').orEmpty}text{/for}";
        assertConverts(input, expected, "site.posts should convert to site.collections.get('posts')");
    }

    @Test
    void testForLoopPropertyIterableGetsOrEmpty() {
        String input = "{% for tag in post.tags %}{{ tag }}{% endfor %}";
        String expected = "{#for tag in post.tags.orEmpty}{=tag}{/for}";
        assertConverts(input, expected, "Property-access iterable in for loop should get .orEmpty");
    }

    @Test
    void testForLoopCdiIterableNoOrEmpty() {
        String input = "{% for item in cdi:books %}text{% endfor %}";
        String expected = "{#for item in cdi:books}text{/for}";
        assertConverts(input, expected, "cdi: iterable should not get .orEmpty");
    }

    @Test
    void testSplitWithDefaultUsesNamespaceForm() {
        // default is stripped, split uses namespace form for null safety
        String input = "{% assign result = foo.bar | default: \"\" | split: \",\" %}{result}";
        String result = converter.convert(input);
        assertTrue(result.contains("{#let result=str:split(foo.bar, \",\")}"),
                "Should use namespace split form: " + result);
    }

    @Test
    void testForLoopSimpleVariableGetsOrEmpty() {
        String input = "{% for a in authors_raw %}{{ a }}{% endfor %}";
        String expected = "{#for a in authors_raw.orEmpty}{=a}{/for}";
        assertConverts(input, expected, "Simple variable iterable in for loop should get .orEmpty");
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
                    "{str:split(post.data.author??, \",\")}",
                    "Standard syntax should use namespace split");
        }

        @Test
        void testForLoop() {
            assertConverts("{% for item in items %}{{item}}{% endfor %}",
                    "{#for item in items.orEmpty}{item}{/for}",
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
