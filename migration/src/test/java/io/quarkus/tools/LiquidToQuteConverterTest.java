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
        String expected = "{=str:split(\"\", \",\").raw}";
        assertConverts(input, expected, "Empty string split should use namespace split");
    }

    @Test
    void testTernaryWithMethodCall() {
        String input = "{=post.data.author ?: \"\".split(\",\")}";
        String expected = "{=post.data.author.or(\"\").split(\",\").raw}";
        assertConverts(input, expected, "Ternary before method call should use .or() for chaining");
    }

    @Test
    void testTernaryWithTrim() {
        String input = "{=page.data.author ?: \"\".trim()}";
        String expected = "{=page.data.author.or(\"\").trim().raw}";
        assertConverts(input, expected, "Ternary with trim should use .or() for chaining");
    }

    @Test
    void testSpaceBeforeMethod() {
        String input = "{=variable .trim()}";
        String expected = "{=variable.trim().raw}";
        assertConverts(input, expected, "Space before method should be removed");
    }

    @Test
    void testStripFilter() {
        String input = "{{text | strip}}";
        String expected = "{=text.trim().raw}";
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
        String expected = "{=str:split(text, \",\").raw}";
        assertConverts(input, expected, "Split filter should use namespace form");
    }

    @Test
    void testSplitFilterWithMixedQuotesInDelimiter() {
        String input = "{% assign parts = content | split: '<div id=\"placeholder\"></div>' %}";
        String result = converter.convert(input);
        assertTrue(result.contains("str:splitRaw(site.pageContent(page), __delim"),
                "Split of page content should use splitRaw to prevent HTML escaping: " + result);
    }

    @Test
    void testContentVariableInLayoutBecomesSitePageContent() {
        String input = "{% assign parts = content | split: ',' %}{{ parts[0] }}";
        String result = converter.convert(input);
        assertTrue(result.contains("str:splitRaw(site.pageContent(page),"),
                "Split of page content should use splitRaw to prevent HTML escaping: " + result);
    }

    @Test
    void testFindFirstPatternConvertsToWhereNot() {
        String input = String.join("\n",
                "{%- assign latest_version = nil -%}",
                "{%- for release in site.data.releases.releases -%}",
                "  {%- unless release.upcoming -%}",
                "    {%- unless latest_version -%}",
                "      {%- assign latest_version = release.version -%}",
                "      {%- assign latest_major = release.version | split: '.' | first -%}",
                "    {%- endunless -%}",
                "  {%- endunless -%}",
                "{%- endfor -%}",
                "Latest {{ latest_major }}.x");
        String result = converter.convert(input);
        assertTrue(result.contains("list:whereNot("),
                "Find-first pattern should use list:whereNot: " + result);
        assertTrue(result.contains("latest_major"),
                "Should preserve latest_major variable: " + result);
        assertFalse(result.contains("{#for"),
                "Find-first loop should be eliminated: " + result);
        assertTrue(result.contains("Latest") && result.contains(".x"),
                "Surrounding content should be preserved: " + result);
    }

    @Test
    void testWhereExpConvertsToNamespaceForm() {
        String input = "{% assign filtered = items | where_exp: \"item\", \"item.active == 'true'\" %}";
        String result = converter.convert(input);
        assertTrue(result.contains("list:whereExp("),
                "where_exp should use list:whereExp namespace form: " + result);
    }

    @Test
    void testWhereExpAccumulatorLoopCollapsed() {
        String input = String.join("\n",
                "{% for query in page.bibquery %}",
                "  {% assign publications = publications | where_exp: \"pub\", query %}",
                "{% endfor %}");
        String result = converter.convert(input);
        assertTrue(result.contains("list:whereExp("),
                "Accumulator loop should collapse to list:whereExp: " + result);
        assertFalse(result.contains("{#for"),
                "Accumulator loop should be eliminated: " + result);
        assertTrue(result.contains("page.data.bibquery") || result.contains("page.bibquery"),
                "Should reference the query list directly: " + result);
    }

    @Test
    void testSplitFilterWithParenInDelimiter() {
        String input = "{{text | split: 'a)b'}}";
        String result = converter.convert(input);
        assertTrue(result.contains("str:split(text, __delim"),
                "Split delimiter containing ) must be hoisted to a {#let} variable: " + result);
    }

    @Test
    void testDefaultBeforeSplitStripsDefault() {
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        // default is stripped
        String expected = "{=str:split(post.data.author, \",\").raw}";
        assertConverts(input, expected, "default before split should be stripped; .data.* gets ");
    }

    @Test
    void testVariableConversion() {
        String input = "{{page.title}}";
        String expected = "{=page.title.raw}";
        assertConverts(input, expected, "Variable output should use alternative syntax");
    }

    @Test
    void testPostKeepsPostPrefix() {
        String input = "{{post.title}}";
        String expected = "{=post.title.raw}";
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
        String expected = "{#for post in site.collections.get('posts').paginated(page.paginator).orEmpty}{=post.title.raw}{/for}";
        assertConverts(input, expected,
                "paginator.posts should convert to Roq collection access");
    }

    @Test
    void testPaginatorPostsSizeGuarded() {
        String input = "{% if paginator.posts.size > 0 %}yes{% endif %}";
        String expected = "{#if page.paginator && site.collections.get('posts').paginated(page.paginator).size > 0}yes{/if}";
        assertConverts(input, expected,
                "paginator.posts.size comparison should have null guard for page.paginator");
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
        String expected = "{=page.paginator.next.raw}";
        assertConverts(input, expected,
                "paginator.next_page_path should convert to page.paginator.next");
    }

    @Test
    void testPaginatorPreviousPagePath() {
        String input = "{{paginator.previous_page_path}}";
        String expected = "{=page.paginator.previous.raw}";
        assertConverts(input, expected,
                "paginator.previous_page_path should convert to page.paginator.previous");
    }

    @Test
    void testAutopagesAuthorVariables() {
        String input = "{% assign author_data = page.pagination.author_data %}{% assign author_id = page.pagination.author %}{{author_data.name}} ({{author_id}})";
        String expected = "{#let author_data=page.data}{#let author_id=page.data._key}{=author_data.name.raw} ({=author_id.raw}){/let}{/let}";
        assertConverts(input, expected,
                "Jekyll autopages author variables should convert to Roq from-data equivalents");
    }

    @Test
    void testAutopagesPaginatorConvertsToFilteredIteration() {
        String input = "{% assign author = page.pagination.author %}"
                + "{% if paginator.posts.size > 0 %}"
                + "{% for post in paginator.posts %}"
                + "{{post.title}}"
                + "{% endfor %}"
                + "{% endif %}";
        String converted = converter.convert(input);
        assertFalse(converted.contains("page.paginator"),
                "Autopages template should not use page.paginator (null on from-data pages), got: " + converted);
        assertTrue(converted.contains(".filter('author', page.data._key)"),
                "Should filter posts by entity key, got: " + converted);
    }

    @Test
    void testAutopagesCategoryVariables() {
        String input = "{% assign cat = page.pagination.category %}{% assign cat_info = page.pagination.category_data %}{{cat_info.title}}";
        String expected = "{#let cat=page.data._key}{#let cat_info=page.data}{=cat_info.title.raw}{/let}{/let}";
        assertConverts(input, expected,
                "Jekyll autopages should work generically for any entity type");
    }

    @Test
    void testForLoop() {
        String input = "{% for item in items %}{{item}}{% endfor %}";
        String expected = "{#for item in items.orEmpty}{=item.raw}{/for}";
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
        String expected = "{=page.date.format('yyyy-MM-dd').or('').raw}";
        assertConverts(input, expected, "Date filter should convert format");
    }

    @Test
    void testUpcase() {
        String input = "{{text | upcase}}";
        String expected = "{=text.toUpperCase.raw}";
        assertConverts(input, expected, "Upcase filter should convert");
    }

    @Test
    void testDowncase() {
        String input = "{{text | downcase}}";
        String expected = "{=text.toLowerCase.raw}";
        assertConverts(input, expected, "Downcase filter should convert");
    }

    @Test
    void testMultipleFilters() {
        String input = "{{text | strip | upcase}}";
        String expected = "{=text.trim().toUpperCase.raw}";
        assertConverts(input, expected, "Multiple filters should chain");
    }

    @Test
    void testAssignment() {
        String input = "{% assign myvar = \"value\" %}";
        String expected = "{#let myvar=\"value\"}{/let}";
        assertConverts(input, expected, "Assignment should convert");
    }

    @Test
    void testAssignmentWithDefaultConvertsToOr() {
        String input = "{% assign posts_limit = site.feed.posts_limit | default: 400 %}";
        String expected = "{#let posts_limit=cdi:siteConfig.feed.posts_limit.or(400)}{/let}";
        assertConverts(input, expected,
                "Assign with default should convert ?: to .or() (Qute {#let} can't handle ?:)");
    }

    @Test
    void testAssignWithChainedDefaults() {
        String input = "{% assign post_author = post.author | default: post.authors[0] | default: site.author %}" +
                "{% if post_author %}x{% endif %}";
        String expected = "{#let post_author=post.data.author.or(post.data.authors.get(0)).or(cdi:siteConfig.author)}" +
                "{#if post_author}x{/if}{/let}";
        assertConverts(input, expected,
                "Chained defaults in assign should convert to .or() chain");
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
        String expected = "{=page_title.raw}";
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
        // default is stripped; .or('') guards against Results$NotFound on JsonObject
        String input = "{{post.author | default: \"\" | split: \",\"}}";
        String expected = "{=str:split(post.data.author, \",\").raw}";
        assertConverts(input, expected, "Real-world author pattern should convert correctly");
    }

    @Test
    void testMultipleTernariesInSameExpression() {
        String input = "{=a ?: \"\".trim()} and {=b ?: \"\".split(\",\")}";
        String expected = "{=a.or(\"\").trim().raw} and {=b.or(\"\").split(\",\").raw}";
        assertConverts(input, expected, "Multiple ternaries should all use .or() for chaining");
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
        String expected = "{=\"hello\".concat(\" world\").raw}";
        assertConverts(input, expected, "Append filter should convert to .concat()");
    }

    @Test
    void testMultipleAppends() {
        String input = "{{\"a\" | append: \"b\" | append: \"c\"}}";
        String expected = "{=\"a\".concat(\"b\").concat(\"c\").raw}";
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
        String expected = "{=array.where(\"key\", \"value\").raw}";
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
        String input = """
                {% assign authors_raw = post.author | default: "" | split: "," %}
                {% assign authors_clean = "" | split: "" %}
                {% for a in authors_raw %}
                {% assign a_trimmed = a | strip %}
                {% if a_trimmed != "" %}
                {% assign authors_clean = authors_clean | push: a_trimmed %}
                {% endif %}
                {% endfor %}
                {% for author_key in authors_clean %}
                {{author_key}}
                {% endfor %}""";
        String expected = """
                {#for author_key in str:splitTrimmed(post.data.author, ",").orEmpty}
                {=author_key.raw}
                {/for}""";
        assertConverts(input, expected,
                "Init-empty-list + push-in-loop + iterate should collapse to str:splitTrimmed");
    }

    @Test
    void testPushInLoopWithHtmlBetween() {
        String input = """
                {% assign authors_raw = post.author | default: "" | split: "," %}
                {% assign authors_clean = "" | split: "" %}
                {% for a in authors_raw %}
                {% assign a_trimmed = a | strip %}
                {% if a_trimmed != "" %}
                {% assign authors_clean = authors_clean | push: a_trimmed %}
                {% endif %}
                {% endfor %}
                <p class="byline">
                By
                {% for author_key in authors_clean %}
                {{author_key}}
                {% endfor %}""";
        String expected = """
                <p class="byline">
                By
                {#for author_key in str:splitTrimmed(post.data.author, ",").orEmpty}
                {=author_key.raw}
                {/for}""";
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

        String expected = """
                      {!  Build multi-author list for this post  !}
                      {#let authors_raw=str:split(post.data.author, ",")}
                      {#let authors_clean=str:split("", "")}{/let}{/let}\
                """;

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
        String expected = "{#for item in items.orEmpty}{=item.raw}{/for}";
        assertConverts(input, expected,
                "endfor with whitespace trimming should convert");
    }

    @Test
    void testReplaceRegexFilter() {
        String input = "{{page.url | replace_regex: '^/version/([^/]+)/.*', '\\1'}}";
        String expected = "{=page.url.path.replaceAll('^/version/([^/]+)/.*', '$1').raw}";
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
        String expected = "{=title.endsWith('Quarkus').raw}";
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
        // site.baseurl is removed (Roq has no baseurl concept), empty concat cleaned up
        String input = "{{paginator.next_page_path | prepend: site.baseurl}}";
        String expected = "{=page.paginator.next.raw}";
        assertConverts(input, expected,
                "Prepend with site.baseurl should simplify to just the expression");
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
        String expected = "{=cdi:authors.get(key).name.raw}";
        assertConverts(input, expected,
                "Bracket notation followed by property access should convert correctly");
    }

    @Test
    void testGetWithPageDataArgument() {
        String input = "{% assign author = site.data.authors[post.author] %}";
        String expected = "{#let author=cdi:authors.get(post.data.author)}{/let}";
        assertConverts(input, expected,
                ".data.* property in .get() argument");
    }

    @Test
    void testAssignBracketWithDefaultPreservesOr() {
        String input = "{% assign post_author = site.data.authors[post_author] | default: post_author %}" +
                "{% if post_author %}x{% endif %}";
        String expected = "{#let post_author=cdi:authors.get(post_author).or(post_author)}" +
                "{#if post_author}x{/if}{/let}";
        assertConverts(input, expected,
                "Bracket access with default should preserve fallback");
    }

    // --- Loop variable tests ---

    @Test
    void testForLoopIndexWithNamedVar() {
        String input = "{% for post in posts %}{{forloop.index0}} {{forloop.index}}{% endfor %}";
        String expected = "{#for post in posts.orEmpty}{=post_index.raw} {=post_count.raw}{/for}";
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
        String expected = "{#for cat in categories.orEmpty}{#for post in cat.posts.orEmpty}{=post_count.raw}{/for}{/for}";
        assertConverts(input, expected,
                "Nested loops should use their own variable name for metadata");
    }

    @Test
    void testForLoopWithLimitAndOffset() {
        String input = "{% for item in items limit:3 offset:2 %}{{item}}{% endfor %}";
        String expected = "{#for item in items.orEmpty}{#if item_count > 2 && item_count <= 2 + 3}{=item.raw}{/if}{/for}";
        assertConverts(input, expected,
                "Loop with limit and offset should use count guard");
    }

    @Test
    void testForLoopWithVariableLimit() {
        String input = "{% for item in items limit: my_limit %}{{item}}{% endfor %}";
        String expected = "{#for item in items.orEmpty}{#if item_count <= my_limit}{=item.raw}{/if}{/for}";
        assertConverts(input, expected,
                "Loop with variable limit should use count guard");
    }

    @Test
    void testXmlEscapeFilter() {
        String input = "{{ post.title | xml_escape }}";
        String expected = "{=post.title.escapeHtml.raw}";
        assertConverts(input, expected, "xml_escape filter should convert to escapeHtml");
    }

    @Test
    void testDateToRfc822Filter() {
        String input = "{{ post.date | date_to_rfc822 }}";
        String expected = "{=post.date.rfc822.raw}";
        assertConverts(input, expected, "date_to_rfc822 filter should convert to .rfc822");
    }

    @Test
    void testSiteTimeRfc822() {
        String input = "{{ site.time | date_to_rfc822 }}";
        String expected = "{=now.rfc822.raw}";
        assertConverts(input, expected,
                "site.time with date_to_rfc822 should use now.rfc822 (LocalDateTime extension)");
    }

    @Test
    void testTernaryWithPropertyStyleMethod() {
        String input = "{=post_author.name ?: \"\".escapeHtml}";
        String expected = "{=post_author.name.or(\"\").escapeHtml.raw}";
        assertConverts(input, expected,
                "Ternary before property-style method should use .or() for chaining");
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
        String expected = "{=variable.trim().raw}";
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
        String expected = "{=page.date.format('hh:mm a').or('').raw}";
        assertConverts(input, expected, "12-hour date format should convert");
    }

    @Test
    void testDateFilterUnknownSpecifier() {
        String input = "{{page.date | date: \"%Y-%Q\"}}";
        String expected = "{=page.date.format('yyyy-%Q /* TODO: unsupported strftime specifiers */').or('').raw}";
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
        String expected = "{#let x=\"hello\"}\n{=x.raw}\nmore content{/let}";
        assertConverts(input, expected,
                "Assign at top level should scope to end of content");
    }

    @Test
    void testAssignScopeEndsAtEnclosingForLoop() {
        String input = "{#for item in items.orEmpty}{% assign x = item.name %}{=x}{/for}after";
        String expected = "{#for item in items.orEmpty}{#let x=item.name}{=x.raw}{/let}{/for}after";
        assertConverts(input, expected,
                "Assign inside a for loop should scope to the loop's end");
    }

    @Test
    void testAssignScopeEndsAtEnclosingIf() {
        String input = "{#if cond}{% assign x = \"val\" %}{=x}{/if}";
        String expected = "{#if cond}{#let x=\"val\"}{=x.raw}{/let}{/if}";
        assertConverts(input, expected,
                "Assign inside an if block should scope to the if's end");
    }

    @Test
    void testMultipleAssignsNestCorrectly() {
        String input = "{% assign a = 1 %}\n{% assign b = 2 %}\n{=a} {=b}";
        String expected = "{#let a=1}\n{#let b=2}\n{=a.raw} {=b.raw}{/let}{/let}";
        assertConverts(input, expected,
                "Multiple top-level assigns should nest with both {/let}s at end");
    }

    @Test
    void testAssignInIfElseBranchesWithFalseElse() {
        String input = "{% if page.title %}{% assign x = page.title %}{% else %}{% assign x = false %}{% endif %}";
        // condition == ifExpr and else is false: use condition directly (null is falsy like false)
        String expected = "{#let x=page.title}\n{/let}";
        assertConverts(input, expected,
                "Same variable in if/else with false else should use condition directly");
    }

    @Test
    void testAssignInIfElseBranchesMethodCallOnCondition() {
        // When if-expr calls a method on the condition and else-expr is false,
        // and the variable is used far from the if/else block:
        // use the direct method call (no ?: which Qute's {#let} can't handle)
        String input = "{% if page.title %}" +
                "{% assign starts = page.title | startswith: 'Quarkus -' %}" +
                "{% else %}" +
                "{% assign starts = false %}" +
                "{% endif %}" +
                "\n\n\n\n\n\n\n\n\n\n" + // many lines between
                "<title>{{ page.title }}{% unless starts %} - Quarkus{% endunless %}</title>";
        String result = new LiquidToQuteConverter().convert(input);
        assertTrue(result.contains("{#let starts=page.title.startsWith('Quarkus -')}"),
                "Should use direct method call without ?: : " + result);
        assertFalse(result.contains("?:"),
                "Must not contain ?: (breaks Qute {#let} parser): " + result);
        // The let block must encompass the <title> line
        int letStart = result.indexOf("{#let starts=");
        int titlePos = result.indexOf("<title>");
        int letEnd = result.indexOf("{/let}", titlePos);
        assertTrue(letStart < titlePos && titlePos < letEnd,
                "Let scope must encompass title tag. Result: " + result);
    }

    @Test
    void testAssignInIfElseBranchesGeneralCase() {
        // Qute {#let} does NOT support ternary (? :) — the parser treats : as a section separator.
        // Instead, trailing content is duplicated into both branches so the scoped variable is visible.
        String input = "{% if page.title %}{% assign x = page.title %}{% else %}{% assign x = 'default' %}{% endif %}" +
                "{= x }";
        String result = converter.convert(input);
        assertTrue(result.contains("{#if page.title}"),
                "If/else should be preserved: " + result);
        assertTrue(result.contains("{#let x=page.title}"),
                "If branch should have scoped let: " + result);
        assertTrue(result.contains("{#let x='default'}"),
                "Else branch should have scoped let: " + result);
        // Usage of x should appear in both branches with .raw
        int firstUsage = result.indexOf("{= x .raw}");
        int secondUsage = result.indexOf("{= x .raw}", firstUsage + 1);
        assertTrue(secondUsage > firstUsage,
                "Trailing content using variable should be duplicated into both branches: " + result);
    }

    @Test
    void testMultipleComplementaryIfBlocksDoNotCrash() {
        // base.html has multiple if/unless pairs — merging one must not corrupt positions
        // for subsequent pairs (regression: StringIndexOutOfBoundsException).
        String input = "{% if x %}{% assign a = x %}{% endif %}" +
                "{% unless x %}{% assign a = 'default' %}{% endunless %}" +
                "{% if y %}{% assign b = y %}{% endif %}" +
                "{% unless y %}{% assign b = 'other' %}{% endunless %}" +
                "{{ a }} {{ b }}";
        String result = converter.convert(input);
        assertTrue(result.contains("{#let a="),
                "First variable should be assigned: " + result);
        assertTrue(result.contains("{#let b="),
                "Second variable should be assigned: " + result);
    }

    @Test
    void testAssignInIfPlusUnlessMergedToIfElse() {
        // Liquid: if X → assign V = A; unless X → assign V = B
        // This is equivalent to if/else and should be handled the same way.
        // The variable must be visible after both blocks.
        String input = "{% if include.page_title %}{% assign page_title = include.page_title %}{% endif %}" +
                "{% unless include.page_title %}{% assign page_title = page.title %}{% endunless %}" +
                "<h1>{{ page_title }}</h1>";
        String result = converter.convert(input);
        // page_title must be defined and visible at the <h1> tag
        assertTrue(result.contains("<h1>") && result.contains("</h1>"),
                "h1 tag should be present: " + result);
        // The h1 must contain the variable, not be empty
        assertFalse(result.contains("<h1>{=page_title??}</h1>") && !result.contains("{#let"),
                "page_title must be scoped to cover the h1 tag: " + result);
        // Should not have dangling assigns inside if blocks that scope-close too early
        assertFalse(result.matches("(?s).*\\{#if[^}]*\\}\\{#let page_title=[^}]*\\}\\{/let\\}\\{/if\\}.*"),
                "assign should not be scoped inside if block (variable won't survive): " + result);
    }

    @Test
    void testNestedIfBlocksDoNotCrashMerge() {
        // Nested {#if} inside another {#if} must not be collected as a
        // sibling for merging — that would cause overlapping positions
        // and a StringIndexOutOfBoundsException.
        String input = "{% if a %}{% if b %}inner{% endif %}outer{% endif %}" +
                "{% unless a %}alt{% endunless %}";
        String result = converter.convert(input);
        assertNotNull(result, "Should not crash on nested if blocks");
        assertTrue(result.contains("inner"), "Nested content preserved: " + result);
        assertTrue(result.contains("alt"), "Unless branch preserved: " + result);
    }

    @Test
    void testReplaceRegexWithPrependChain() {
        String input = "{% assign x = page.url | replace_regex: '^/version/([^/]+)/.*', '\\1' | prepend: ' - ' %}";
        String expected = "{#let x=' - '.concat(page.url.path.replaceAll('^/version/([^/]+)/.*', '$1'))}{/let}";
        assertConverts(input, expected,
                "replace_regex chained with prepend should produce valid method call");
    }

    @Test
    void testChainedFiltersRemoveSpaces() {
        String input = "{{page.content | strip_html | truncatewords: 75}}";
        String expected = "{=page.content.stripHtml.wordLimit(75).raw}";
        assertConverts(input, expected,
                "Chained filters should not have spaces between method calls");
    }

    @Test
    void testPostCustomFieldConvertToData() {
        String input = "{{post.author}}";
        String expected = "{=post.data.author.or('').raw}";
        assertConverts(input, expected,
                "post.customField should convert to post.data.customField with .or('') safety");
    }

    @Test
    void testPostBuiltInFieldsNotConverted() {
        String input = "{{post.title}} {{post.url}} {{post.date}} {{post.content}}";
        String expected = "{=post.title.raw} {=post.url.raw} {=post.date.raw} {=post.content.raw}";
        assertConverts(input, expected,
                "post built-in properties should not be converted to post.data.*");
    }

    @Test
    void testPostSynopsisConverted() {
        String input = "{#if post.synopsis}yes{/if}";
        String expected = "{#if post.data.synopsis}yes{/if}";
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
        String expected = "<a href=\"{=''}/path\">Link</a>";
        assertConverts(input, expected,
                "site.baseurl should convert to empty string (Roq has no baseurl)");
    }

    @Test
    void testSiteLanguageConvertsToCdi() {
        String input = "<html lang=\"{{site.language}}\">";
        String expected = "<html lang=\"{=cdi:siteConfig.language.raw}\">";
        assertConverts(input, expected,
                "site.language should convert to CDI reference");
    }

    @Test
    void testSiteBaseurlInConditional() {
        String input = "{% if site.baseurl %}<base href=\"{{site.baseurl}}\">{% endif %}";
        String expected = "{#if ''}<base href=\"{=''}\">{/if}";
        assertConverts(input, expected,
                "site.baseurl in conditionals should convert to empty string");
    }

    @Test
    void testSiteCustomPropertyConvertsToCdi() {
        String input = "{=site.twitter_username}";
        String expected = "{=cdi:siteConfig.twitter_username.raw}";
        assertConverts(input, expected,
                "Custom site properties should convert to cdi:siteConfig references");
    }

    @Test
    void testSiteBuiltInPropertiesNotConverted() {
        String input = "{=site.url} {=site.title} {=site.collections} {=site.pages} {=site.data}";
        String expected = "{=site.url.root.url.raw} {=site.title.raw} {=site.collections.raw} {=site.pages.raw} {=site.data.raw}";
        assertConverts(input, expected,
                "Roq Site built-in properties should not be converted to cdi:siteConfig");
    }

    @Test
    void testStandaloneSiteUrlConvertsToAbsolute() {
        assertConverts("<loc>{{ site.url }}/</loc>",
                "<loc>{=site.url.root.url.raw}/</loc>",
                "Standalone site.url should use .root.url for absolute URL (Jekyll site.url is a base URL string)");
    }

    @Test
    void testSiteUrlWithConcatenatedLinkUrl() {
        assertConverts("<loc>{{ site.url }}{{ link.url }}</loc>",
                "<loc>{=site.url.root.url.raw}{=link.url.raw}</loc>",
                "site.url followed by another expression should still convert to .root.url");
    }

    @Test
    void testSiteUrlNotConvertedWhenFollowedByMethodCall() {
        String input = "{=site.url.resolve(page.url)}";
        String expected = "{=site.url.resolve(page.url).raw}";
        assertConverts(input, expected,
                "site.url followed by .resolve() should not be double-converted");
    }

    @Test
    void testSiteHyphenatedPropertyConvertsToCamelCase() {
        String input = "{=site.search.cached-script-file}";
        String expected = "{=cdi:siteConfig.search.cachedScriptFile.raw}";
        assertConverts(input, expected,
                "Hyphenated site config keys should be camelCased for Qute dot notation");
    }

    @Test
    void testSiteNestedHyphenatedChain() {
        String input = "{% assign x = site.search.script-mode %}";
        String result = converter.convert(input);
        assertTrue(result.contains("cdi:siteConfig.search.scriptMode"),
                "Nested hyphenated keys should be camelCased: " + result);
    }

    @Test
    void testSiteHyphenatedPropertyWithRelativeUrl() {
        String input = "{% assign search_script_src = site.search.cached-script-file | relative_url %}";
        String result = converter.convert(input);
        assertTrue(result.contains("cdi:siteConfig.search.cachedScriptFile"),
                "Hyphenated YAML key with relative_url filter should be camelCased: " + result);
        assertFalse(result.contains("cachedScriptCdi"),
                "CamelCase should not bleed into cdi: prefix: " + result);
    }

    @Test
    void testCustomPageFieldConvertToData() {
        String input = "{{page.data.author}}";
        String expected = "{=page.data.author.or('').raw}";
        assertConverts(input, expected,
                "Custom page data output should get .or('') before .raw for missing-key safety");
    }

    @Test
    void testBuiltInPageFieldsNotConverted() {
        String input = "{{page.title}} {{page.date}} {{page.url}}";
        String expected = "{=page.title.raw} {=page.date.raw} {=page.url.raw}";
        assertConverts(input, expected,
                "Built-in page properties should not be converted to page.data.*");
    }

    @Test
    void testMultipleCustomPageFields() {
        String input = "{{page.data.author}} - {{page.synopsis}}";
        String expected = "{=page.data.author.or('').raw} - {=page.data.synopsis.or('').raw}";
        assertConverts(input, expected,
                "Custom page data outputs should get .or('') for missing-key safety");
    }

    @Test
    void testCustomFieldInConditional() {
        String input = "{% if page.search_wc %}...{% endif %}";
        String expected = "{#if page.data.search_wc}...{/if}";
        assertConverts(input, expected,
                "Custom fields in conditionals should not get ?? (breaks JsonObject key lookup)");
    }

    @Test
    void testSiteSearchConvertsToCdi() {
        String input = "{{site.search.host}}";
        String expected = "{=cdi:siteConfig.search.host.raw}";
        assertConverts(input, expected,
                "site.search properties should convert to CDI reference");
    }

    @Test
    void testSiteSearchScriptMode() {
        String input = "{% if site.search.script-mode == 'direct' %}...{% endif %}";
        String expected = "{#if cdi:siteConfig.search.scriptMode == 'direct'}...{/if}";
        assertConverts(input, expected,
                "site.search.script-mode should convert to camelCase CDI reference");
    }

    @Test
    void testUrlConcatenationWithSiteUrl() {
        String input = "{{site.url | append: page.url}}";
        String expected = "{=site.url.resolve(page.url).raw}";
        assertConverts(input, expected,
                "URL concatenation should use .resolve() instead of +");
    }

    @Test
    void testUrlConcatenationWithVariable() {
        String input = "{{canonical_url | prepend: site.url}}";
        String expected = "{=site.url.resolve(canonical_url).raw}";
        assertConverts(input, expected,
                "Prepending to URL should use .resolve()");
    }

    @Test
    void testPageUrlEqualityComparison() {
        String input = "{#if page.url == '/'}homepage{#else}{=page.data.layout}{/if}";
        String expected = "{#if page.url.path == '/'}homepage{#else}{=page.data.layout.or('').raw}{/if}";
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
        assertConverts("{{ site.data.projectfooter.links }}", "{=cdi:projectfooter.links.raw}",
                "site.data.X should convert to cdi:X");
    }

    @Test
    void testSiteDataNestedReference() {
        assertConverts("{{ site.data.versions.documentation }}",
                "{=cdi:versions.documentation.raw}",
                "site.data.X.Y should convert to cdi:X.Y");
    }

    @Test
    void testSiteDataWithBracketAccessConvertsToeCdi() {
        assertConverts("{% if site.data.versioned[docversion_index].index %}yes{% endif %}",
                "{#if cdi:versioned.get(docversion_index).index}yes{/if}",
                "site.data.X[var] should convert to cdi:X.get(var)");
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
    void testAssignInIfElseBlockGeneralCaseDuplicatesTrailing() {
        String input = """
                {% if page.data.layout == 'guides' %}
                  {%assign canonical_url = page.url | replace: 'foo', '' %}
                {% else %}
                  {%assign canonical_url = page.url %}
                {% endif %}
                <link rel="canonical" href="{{ canonical_url }}">
                """;
        String result = converter.convert(input);

        // Qute {#let} does NOT support ternary — trailing content is duplicated into both branches
        assertTrue(result.contains("{#if page.data.layout == 'guides'"),
                "If/else should be preserved: " + result);
        assertTrue(result.contains("canonical_url=page.url.path.replace('foo', '')"),
                "If branch should have scoped let with filter: " + result);
        // Trailing <link> line should appear in both branches
        int firstLink = result.indexOf("<link rel=\"canonical\"");
        int secondLink = result.indexOf("<link rel=\"canonical\"", firstLink + 1);
        assertTrue(secondLink > firstLink,
                "Trailing content using variable should be duplicated into both branches: " + result);
    }

    @Test
    void testRelativeUrlFilter() {
        String input = "{{ '/assets/javascript/highlight.pack.js' | relative_url }}";
        String expected = "{='/assets/javascript/highlight.pack.js'}";
        assertConverts(input, expected, "relative_url filter is a no-op in Roq");
    }

    @Test
    void testRelativeUrlFilterWithVariable() {
        String input = "{{ page.url | relative_url }}";
        String expected = "{='/'.concat(page.url).raw}";
        assertConverts(input, expected, "relative_url filter on variable prepends /");
    }

    @Test
    void testAbsoluteUrlFilter() {
        String input = "{{ '/feed.xml' | absolute_url }}";
        String expected = "{='/feed.xml'}";
        assertConverts(input, expected, "absolute_url filter is a no-op in Roq");
    }

    @Test
    void testPrependWithStringLiteral() {
        String input = "{{ '/assets/images/quarkus_card.png' | prepend: site.url }}";
        String expected = "{=site.url.resolve('/assets/images/quarkus_card.png').raw}";
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
        String expected = "<div>{=page.content.raw}</div>";
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
        String expected = "{#for tag in post.tags.orEmpty}{=tag.raw}{/for}";
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
        String expected = "{#for a in authors_raw.orEmpty}{=a.raw}{/for}";
        assertConverts(input, expected, "Simple variable iterable in for loop should get .orEmpty");
    }

    @Test
    void testNowDateFilterConvertsToQuteGlobal() {
        String input = "{% assign today = 'now' | date: '%Y-%m-%d' %}";
        String expected = "{#let today=now.format('yyyy-MM-dd').or('')}{/let}";
        assertConverts(input, expected, "'now' | date should use Qute now global, not string literal");
    }

    @Test
    void testDateFilterOnNullableFieldGetsOrEmpty() {
        String input = "{% assign eol_str = release.eol_date | date: '%Y-%m-%d' %}";
        String expected = "{#let eol_str=release.eol_date.format('yyyy-MM-dd').or('')}{/let}";
        assertConverts(input, expected, "Date format on nullable field should add .or('') for null safety");
    }

    @Test
    void testNilConvertsToNull() {
        String input = "{% if release.eol_date == nil %}active{% endif %}";
        String expected = "{#if release.eol_date == null}active{/if}";
        assertConverts(input, expected, "nil should convert to null in Qute comparisons");
    }

    @Test
    void testNilNotEqualsConvertsToNull() {
        String input = "{% if release.eol_date != nil %}has date{% endif %}";
        String expected = "{#if release.eol_date ne null}has date{/if}";
        assertConverts(input, expected, "!= nil should convert to ne null");
    }

    @Test
    void testUnlessNilConvertsToNull() {
        String input = "{% unless recommended_lts %}no lts{% endunless %}";
        String expected = "{#if !recommended_lts}no lts{/if}";
        assertConverts(input, expected, "unless with simple variable should negate");
    }

    @Test
    void testUnlessGreaterThanFlipsOperator() {
        String input = "{% unless eol_str > today %}expired{% endunless %}";
        String expected = "{#if eol_str <= today}expired{/if}";
        assertConverts(input, expected, "unless with > should flip to <=");
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
            assertConverts("{{page.title}}", "{page.title.raw}",
                    "Standard syntax should use {expr} not {=expr}");
        }

        @Test
        void testFilter() {
            assertConverts("{{text | upcase}}", "{text.toUpperCase.raw}",
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
                    "{page.content.stripHtml.wordLimit(75).raw}",
                    "Standard syntax should chain filters correctly");
        }

        @Test
        void testTernaryWrapping() {
            assertConverts("{{post.author | default: \"\" | split: \",\"}}",
                    "{str:split(post.data.author, \",\").raw}",
                    "Standard syntax should use namespace split with .or('') for .data.*");
        }

        @Test
        void testForLoop() {
            assertConverts("{% for item in items %}{{item}}{% endfor %}",
                    "{#for item in items.orEmpty}{item.raw}{/for}",
                    "Standard syntax for loop should use {expr} for outputs");
        }

        @Test
        void testSpaceRemoval() {
            assertConverts("{variable .trim()}", "{variable.trim().raw}",
                    "Standard syntax should remove spaces before methods in expressions");
        }

        @Test
        void testExtensionSyntaxDefaultConstructor() {
            LiquidToQuteConverter ext = new LiquidToQuteConverter();
            assertEquals("{=page.title.raw}", ext.convert("{{page.title}}"),
                    "Default constructor should use extension syntax");
        }
    }

    @Test
    void testGroupByThenSortChaining() {
        String input = "{% assign groups = publications | group_by: site.publication.group_by | sort: \"name\" %}";
        String result = converter.convert(input);
        assertTrue(result.contains(".groupBy(") && result.contains(").sort("),
                "group_by | sort should chain as .groupBy(arg).sort(arg), not nest sort inside groupBy arg: " + result);
        assertFalse(result.contains("group_by.sort("),
                "sort should not be nested inside the groupBy argument: " + result);
    }

    @Test
    void testPushInNestedLoopCollapsedToMergeTypes() {
        String input = "{% assign v_type = include.type %}\n" +
                "{% assign values = \"\" | split: \",\" %}\n" +
                "{% for source in index -%}\n" +
                "    {% for item in source[1].types[v_type] -%}\n" +
                "        {% assign values = values | push: item %}\n" +
                "    {% endfor -%}\n" +
                "{% endfor -%}\n" +
                "{% assign values = values | sort: 'title' %}";
        String result = converter.convert(input);
        assertTrue(result.contains("mergeTypes("),
                "Nested push-in-loop with sort should collapse to mergeTypes(): " + result);
        assertFalse(result.contains(".push("),
                "push() should be eliminated by mergeTypes collapse: " + result);
        assertFalse(result.contains(".sort("),
                "sort() should be eliminated by mergeTypes collapse (sorting is built-in): " + result);
    }

    // ── Mutable assign tests ──────────────────────────────────────────────

    @Test
    void testMutableBooleanFlag() {
        // assign false + conditional assign true = classic mutable flag
        String input = "{% assign active = false %}" +
                "{#if cond}{% assign active = true %}{/if}" +
                "{#if active}yes{/if}";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.assign('active', false)"),
                "Default assign should use mutable map: " + result);
        assertTrue(result.contains("_m.assign('active', true)"),
                "Conditional assign should use mutable map: " + result);
        assertTrue(result.contains("_m.read('active')"),
                "Read of mutable var should use _m.read(): " + result);
        assertTrue(result.contains("{#let _m=mut:map()}"),
                "Should wrap with mutable map init: " + result);
        assertFalse(result.contains("{#let active="),
                "Mutable var should NOT use {#let}: " + result);
    }

    @Test
    void testMutableAssignInsideBlockUsedOutside() {
        // Single assign inside a loop, referenced after the loop
        String input = "{#for item in list.orEmpty}" +
                "{#if item.best}{% assign winner = item %}{/if}" +
                "{/for}" +
                "{#if winner}{=winner.name}{/if}";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.assign('winner', item)"),
                "Assign inside loop should use mutable map: " + result);
        assertTrue(result.contains("_m.read('winner')"),
                "Read after loop should use _m.read(): " + result);
    }

    @Test
    void testMutableIfElseAssignUsedOutsideScope() {
        // Assigns at same depth in if/else branches, but variable used outside the block
        String input = "{#if enabled}" +
                "{#if mode}{% assign src = a %}{#else}{% assign src = b %}{/if}" +
                "{/if}" +
                "<script src=\"{=src}\"></script>";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.assign('src',"),
                "If/else assigns used outside scope should use mutable map: " + result);
        assertTrue(result.contains("_m.read('src')"),
                "Read outside scope should use _m.read(): " + result);
    }

    @Test
    void testSingleAssignStaysAsLet() {
        // Single assign, used only within scope — no mutable treatment needed
        String input = "{% assign x = \"hello\" %}\n{=x}\nmore";
        String result = converter.convert(input);
        assertTrue(result.contains("{#let x=\"hello\"}"),
                "Single-scope assign should stay as {#let}: " + result);
        assertFalse(result.contains("mut:map()"),
                "Should NOT use mutable map for simple assigns: " + result);
    }

    @Test
    void testMutableCompoundCondition() {
        // Two mutable flags combined with || in a condition
        String input = "{% assign a = false %}{% assign b = false %}" +
                "{#if x}{% assign a = true %}{/if}" +
                "{#if y}{% assign b = true %}{/if}" +
                "{#if a || b}active{/if}";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.read('a') || _m.read('b')"),
                "Compound condition should replace both vars with _m.read(): " + result);
    }

    @Test
    void testMutableNilInit() {
        // assign nil should convert to null in mutable map
        String input = "{% assign found = nil %}" +
                "{#for item in list.orEmpty}{% assign found = item %}{/for}" +
                "{#if found}yes{/if}";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.assign('found', null)"),
                "nil should convert to null in mutable assign: " + result);
    }

    @Test
    void testMutableAssignPreservesPropertyAccess() {
        // _m.read('var').property should work for object access
        String input = "{% assign chosen = nil %}" +
                "{#for r in list.orEmpty}{#if r.good}{% assign chosen = r %}{/if}{/for}" +
                "{=chosen.name}";
        String result = converter.convert(input);
        assertTrue(result.contains("_m.read('chosen').name"),
                "Property access on mutable var should chain on _m.read(): " + result);
    }

    @Test
    void testMutableMapInitAfterFrontMatter() {
        String input = "---\nlayout: base\n---\n" +
                "{% assign active = false %}" +
                "{#if cond}{% assign active = true %}{/if}" +
                "{#if active}yes{/if}";
        String result = converter.convert(input);
        assertTrue(result.startsWith("---\nlayout: base\n---\n{#let _m=mut:map()}"),
                "Mutable map init should come after front matter, not before: " + result);
    }

    @Test
    void testForLoopRebindingExcludesMutableTreatment() {
        // When a variable is assigned in one loop and then reused as a loop variable
        // name in a subsequent {%for VAR in%}, the for-loop creates a new binding.
        // The assign should NOT get mutable treatment.
        String input = "{% for raw in list %}" +
                "{% assign key = raw | strip %}" +
                "{% assign clean = clean | push: key %}" +
                "{% endfor %}" +
                "{% for key in clean %}" +
                "{=data[key]}" +
                "{% endfor %}";
        String result = converter.convert(input);
        assertFalse(result.contains("_m.assign"),
                "Variable rebound as for-loop var should not use mutable map: " + result);
        assertFalse(result.contains("_m.read"),
                "Variable rebound as for-loop var should not use mutable map: " + result);
    }
}
