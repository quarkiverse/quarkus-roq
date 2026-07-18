package io.quarkus.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiquidToQuteConverter {

    private final boolean useExtensionSyntax;
    private final String exprOpen;
    private final List<String> conversionsApplied = new ArrayList<>();
    private final Map<String, String> splitDelimHoists = new HashMap<>();
    private boolean convertingPartials;

    public LiquidToQuteConverter() {
        this(true);
    }

    public LiquidToQuteConverter(boolean useExtensionSyntax) {
        this.useExtensionSyntax = useExtensionSyntax;
        this.exprOpen = useExtensionSyntax ? "{=" : "{";
    }

    void setConvertingPartials(boolean convertingPartials) {
        this.convertingPartials = convertingPartials;
    }

    public String convert(String content) {
        String original = content;
        splitDelimCounter = 0;
        splitDelimHoists.clear();

        // Strip Liquid whitespace-trimming markers before any conversion
        content = content.replaceAll("\\{%-", "{%");
        content = content.replaceAll("-%\\}", "%}");

        // Extract raw blocks before any conversion to preserve their content verbatim
        List<String> rawBlocks = new ArrayList<>();
        content = extractRawBlocks(content, rawBlocks);

        // Convert in order of complexity
        content = convertComments(content);
        content = convertVariables(content);
        content = convertFindFirstPattern(content);
        content = collapseWhereExpAccumulatorLoop(content);
        content = convertFilters(content);
        content = convertLoops(content);
        content = convertConditionals(content);
        content = convertPaginator(content);
        content = convertIncludes(content);
        content = convertIncludeParamAccess(content);

        // Merge consecutive {#if COND}...{/if}{#if !COND}...{/if} into if/else
        // (from Liquid's {% if %}...{% unless %} pattern, must run before convertIfElseAssigns)
        content = mergeComplementaryIfBlocks(content);

        // Convert if/else blocks with assigns to ternary expressions (must run before convertAssignments)
        content = convertIfElseAssignsToTernary(content);

        // Detect variables that escape their {#let} scope (assigned inside a block
        // but read outside, or assigned more than once) and route them through a
        // MutableMap instead of block-scoped {#let}.  Must run after the ternary
        // pass (which resolves some multi-assigns) and before convertAssignments.
        content = convertMutableAssigns(content);

        content = convertAssignments(content);

        // Collapse "init empty list + push in loop + iterate" into str:splitTrimmed
        // Must run after convertAssignments (which creates {#let}) and convertLoops
        content = collapsePushInLoopPattern(content);

        content = convertCaseStatements(content);
        content = convertLayoutTags(content);
        content = convertSpecialTags(content);

        content = convertBracketNotation(content);

        // Collapse "init empty list + push in nested hash loop + sort" into mergeTypes()
        // Must run after convertBracketNotation (which creates .get() calls from bracket notation)
        content = collapsePushInNestedLoopToMergeTypes(content);

        // Final cleanup steps - ORDER MATTERS!
        // Remove spaces first so ternary wrapping can match properly
        content = removeSpacesBeforeMethods(content);
        content = wrapTernaryBeforeMethods(content);

        // Convert site.data.X references to cdi:X (Roq data file access)
        content = convertSiteDataReferences(content);

        // Roq has no baseurl concept — URLs are already site-relative
        content = content.replaceAll("\\bsite\\.baseurl\\b", "''");

        // Convert site properties that come from data/site.yml to CDI references
        content = convertSiteDataProperties(content);

        // Convert custom page frontmatter fields to page.data.*
        content = convertCustomPageFields(content);

        // Convert Jekyll autopages variables to Roq from-data equivalents
        content = convertAutopagesVariables(content);

        // Make page.data.* references lenient — custom frontmatter may not exist on every page
        content = makePageDataLenient(content);

        // Convert URL concatenation to RoqUrl methods
        content = convertUrlConcatenation(content);

        // Jekyll's site.url is a plain string (e.g. "https://quarkus.io").
        // Roq's site.url is a RoqUrl whose toString() returns the relative path.
        // Convert remaining standalone site.url to site.url.root.url for equivalence.
        content = convertStandaloneSiteUrl(content);

        // Convert page.url equality comparisons to use .path (RoqUrl is not a String)
        content = convertPageUrlComparisons(content);

        // Convert Jekyll site.posts to Roq collections access
        content = convertSiteCollections(content);

        // Make site.tags lenient (needed until  (https://github.com/quarkiverse/quarkus-roq/issues/964 is fixed in a release)
        content = makeSiteTagsLenient(content);

        // Liquid {{ }} never HTML-escapes; Qute {= } does. Append .raw for fidelity.
        content = appendRawToOutputExpressions(content);

        // Wrap any remaining hoisted split delimiter references in {#let}
        content = wrapHoistedSplitDelimiters(content);

        // Clean up empty-string concatenation from baseurl removal
        content = content.replaceAll("'' \\+ ", "");
        content = content.replaceAll(" \\+ ''", "");
        content = content.replaceAll("''\\.concat\\(([^)]+)\\)", "$1");

        // Restore raw blocks with Qute verbatim delimiters
        content = restoreRawBlocks(content, rawBlocks);

        if (!content.equals(original)) {
            conversionsApplied.add("Template converted successfully");
        }

        return content;
    }

    String getConversionReport() {
        if (conversionsApplied.isEmpty()) {
            return "No conversions needed";
        }
        return conversionsApplied.stream()
                .map(s -> "✓ " + s)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    void clearConversions() {
        conversionsApplied.clear();
    }

    private String extractRawBlocks(String content, List<String> rawBlocks) {
        Pattern pattern = Pattern.compile("\\{%\\s*raw\\s*%\\}(.*?)\\{%\\s*endraw\\s*%\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            rawBlocks.add(matcher.group(1));
            matcher.appendReplacement(sb, "\0RAW_" + (rawBlocks.size() - 1) + "\0");
        }
        matcher.appendTail(sb);
        if (!rawBlocks.isEmpty()) {
            conversionsApplied.add("Converted raw blocks");
        }
        return sb.toString();
    }

    private String restoreRawBlocks(String content, List<String> rawBlocks) {
        for (int i = 0; i < rawBlocks.size(); i++) {
            content = content.replace("\0RAW_" + i + "\0", "{|" + rawBlocks.get(i) + "|}");
        }
        return content;
    }

    private String convertComments(String content) {
        // Liquid: {% comment %}...{% endcomment %}
        // Qute: {! ... !}
        Pattern pattern = Pattern.compile("\\{%\\s*comment\\s*%\\}(.*?)\\{%\\s*endcomment\\s*%\\}", Pattern.DOTALL);
        String result = pattern.matcher(content).replaceAll("{! $1 !}");

        if (!result.equals(content)) {
            conversionsApplied.add("Converted comments");
        }

        return result;
    }

    private String convertVariables(String content) {
        // Liquid: {{ variable }}
        // Qute (Roq alternative syntax): {=variable}
        Pattern pattern = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String var = matcher.group(1).trim();

            // Note: post.* is NOT converted to page.* here because post may be a loop variable
            // (e.g., {#for post in site.collections.get('posts')}). In Roq, iterated items
            // are page-like objects so post.title, post.url etc. work directly.

            matcher.appendReplacement(sb, Matcher.quoteReplacement(exprOpen) + Matcher.quoteReplacement(var) + "}");
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted variable outputs to alternative expression syntax");
        }

        return result;
    }

    private String convertFilters(String content) {
        Pattern blockPattern = useExtensionSyntax
                ? Pattern.compile("\\{=[^}]*\\}|\\{%(?:[^%]|%(?!\\}))*%\\}")
                : Pattern.compile("\\{(?![%#/!|])[^}]*\\}|\\{%(?:[^%]|%(?!\\}))*%\\}");
        Matcher matcher = blockPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String block = matcher.group();
            String converted = convertFiltersInBlock(block);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(converted));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String convertFiltersInBlock(String block) {
        // Convert method-call filters first so concatenation filters see clean expressions
        block = convertTwoArgFilters(block);
        block = convertWhereExpFilter(block);
        block = convertTableDrivenFilters(block);
        block = convertDateFilter(block);
        block = stripDefaultBeforeSplit(block);
        block = convertDefaultFilter(block);
        block = convertPushFilter(block);
        block = convertSplitFilter(block);
        block = convertUrlFilters(block);
        // Concatenation filters (append/prepend) run last since they need
        // the base expression fully converted to method calls
        block = convertConcatenationFilters(block);
        return block;
    }

    private String convertUrlFilters(String content) {
        // relative_url on a string literal starting with '/' is a no-op in Roq (already absolute)
        // relative_url on a variable needs to prepend '/' since the value may be a bare path
        content = content.replaceAll(
                "'(/[^']*)'\\s*\\|\\s*relative_url", "'$1'");
        content = content.replaceAll(
                "([a-zA-Z_][a-zA-Z0-9_.\\-]*)\\s*\\|\\s*relative_url", "'/'.concat($1)");
        content = content.replaceAll("\\s*\\|\\s*absolute_url", "");
        return content;
    }

    private String convertConcatenationFilters(String content) {
        // Append filter: "text" | append: variable | append: "more" -> "text".concat(variable).concat("more")
        // Uses .concat() instead of + because Qute's {#let} doesn't support the + operator.
        Pattern appendPattern = Pattern.compile("([^|{]+?)((?:\\s*\\|\\s*append:\\s*[^|}%]+)+)");
        Matcher appendMatcher = appendPattern.matcher(content);
        StringBuilder appendSb = new StringBuilder();

        while (appendMatcher.find()) {
            String base = appendMatcher.group(1).trim();
            String appends = appendMatcher.group(2);

            Pattern appendValuePattern = Pattern.compile("\\|\\s*append:\\s*([^|}%]+?)(?=\\s*\\||$)");
            Matcher appendValueMatcher = appendValuePattern.matcher(appends);
            StringBuilder concatenation = new StringBuilder(base);

            while (appendValueMatcher.find()) {
                concatenation.append(".concat(").append(appendValueMatcher.group(1).trim()).append(")");
            }

            String remaining = appends.replaceAll("\\|\\s*append:\\s*[^|]+", "").trim();
            String replacement;
            if (!remaining.isEmpty() && remaining.startsWith("|")) {
                replacement = "(" + concatenation + ")" + remaining;
            } else {
                replacement = concatenation.toString();
            }

            appendMatcher.appendReplacement(appendSb, Matcher.quoteReplacement(replacement));
        }
        appendMatcher.appendTail(appendSb);

        String result = appendSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted append filter to .concat()");
            content = result;
        }

        // Prepend filter: expr | prepend: value -> value + expr
        // Uses paren-aware backward walk to handle method calls like .replaceAll(...)
        content = convertPrependFilter(content);

        return content;
    }

    private String convertPrependFilter(String content) {
        Pattern prependPipe = Pattern.compile("\\|\\s*prepend:\\s*([^|}%]+)");

        while (true) {
            Matcher m = prependPipe.matcher(content);
            if (!m.find())
                break;

            String value = m.group(1).trim();
            int pipeStart = m.start();

            int baseEnd = pipeStart;
            while (baseEnd > 0 && Character.isWhitespace(content.charAt(baseEnd - 1)))
                baseEnd--;

            int baseStart = baseEnd;
            int parenDepth = 0;
            while (baseStart > 0) {
                char c = content.charAt(baseStart - 1);
                if (c == ')') {
                    parenDepth++;
                    baseStart--;
                } else if (c == '(') {
                    if (parenDepth > 0) {
                        parenDepth--;
                        baseStart--;
                    } else {
                        break;
                    }
                } else if (parenDepth > 0) {
                    baseStart--;
                } else if (c == '\'' || c == '"') {
                    // Walk backward through the entire string literal
                    char quote = c;
                    baseStart--;
                    while (baseStart > 0 && content.charAt(baseStart - 1) != quote) {
                        baseStart--;
                    }
                    if (baseStart > 0) {
                        baseStart--; // include the opening quote
                    }
                } else if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == ':') {
                    baseStart--;
                } else {
                    break;
                }
            }

            if (baseStart >= baseEnd) {
                break;
            }

            String base = content.substring(baseStart, baseEnd);
            content = content.substring(0, baseStart) + value + ".concat(" + base + ")" + content.substring(m.end());
            conversionsApplied.add("Converted prepend filter to .concat()");
        }

        return content;
    }

    private String convertTableDrivenFilters(String content) {
        // Filters with a single argument: | filter: arg -> .method(arg)
        String[][] filterWithArgMap = {
                { "group_by", "groupBy" },
                { "sort", "sort" },
                { "startswith", "startsWith" },
                { "endswith", "endsWith" },
                { "contains", "contains" },
                { "equals", "equals" },
                { "map", "map" },
                { "slice", "slice" },
                { "add", "add" },
                { "minus", "minus" },
                { "times", "times" },
                { "truncate", "truncate" },
                { "remove_first", "removeFirst" },
        };

        for (String[] mapping : filterWithArgMap) {
            String liquidFilter = mapping[0];
            String quteMethod = mapping[1];
            Pattern fwaPattern = Pattern.compile("\\s*\\|\\s*" + liquidFilter + ":\\s*([^|}%]+)");
            Matcher fwaMatcher = fwaPattern.matcher(content);
            StringBuilder fwaSb = new StringBuilder();

            while (fwaMatcher.find()) {
                String arg = fwaMatcher.group(1).trim();
                fwaMatcher.appendReplacement(fwaSb, "." + quteMethod + "(" + Matcher.quoteReplacement(arg) + ")");
            }
            fwaMatcher.appendTail(fwaSb);

            String result = fwaSb.toString();
            if (!result.equals(content)) {
                conversionsApplied.add("Converted filter: " + liquidFilter + " -> " + quteMethod + "()");
                content = result;
            }
        }

        // No-arg filters: | filter -> .method
        String[][] filterMap = {
                { "upcase", "toUpperCase" },
                { "downcase", "toLowerCase" },
                { "capitalize", "capitalize" },
                { "strip_html", "stripHtml" },
                { "number_of_words", "numberOfWords" },
                { "size", "size" },
                { "first", "first" },
                { "last", "last" },
                { "join", "join" },
                { "sort", "sort" },
                { "reverse", "reverse" },
                { "uniq", "distinct" },
                { "compact", "filterNotNull" },
                { "strip", "trim()" },
                { "lstrip", "trimStart" },
                { "rstrip", "trimEnd" },
                { "xml_escape", "escapeHtml" },
                { "escape", "escapeHtml" },
                { "date_to_rfc822", "rfc822" },
                { "url_encode", "urlEncode" },
                { "slugify", "slugify" },
                { "markdownify", "markdownify" }
        };

        for (String[] mapping : filterMap) {
            String liquidFilter = mapping[0];
            String quteFilter = mapping[1];
            Pattern pattern = Pattern.compile("\\s*\\|\\s*" + liquidFilter + "\\b");
            String replacement = "." + quteFilter;
            String newContent = pattern.matcher(content).replaceAll(replacement);
            if (!newContent.equals(content)) {
                conversionsApplied.add("Converted filter: " + liquidFilter + " -> " + quteFilter);
                content = newContent;
            }
        }

        return content;
    }

    private String convertWhereExpFilter(String content) {
        // Convert: base | where_exp: "loopVar", expr -> list:whereExp(base, "loopVar", expr)
        Pattern whereExpPattern = Pattern.compile(
                "([a-zA-Z0-9_\\.\\[\\]()]+)\\s*\\|\\s*where_exp:\\s*(\"[^\"]*\"|'[^']*')\\s*,\\s*([^|}%]+)");
        Matcher m = whereExpPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (m.find()) {
            String base = m.group(1).trim();
            String loopVar = m.group(2).trim();
            String expr = m.group(3).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "list:whereExp(" + base + ", " + loopVar + ", " + expr + ")"));
            found = true;
        }
        m.appendTail(sb);
        if (found) {
            conversionsApplied.add("Converted where_exp to list:whereExp namespace form");
            return sb.toString();
        }
        return content;
    }

    private String convertDateFilter(String content) {
        Pattern datePattern = Pattern.compile("\\s*\\|\\s*date:\\s*[\"']([^\"']+)[\"']");
        Matcher dateMatcher = datePattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (dateMatcher.find()) {
            String liquidFormat = dateMatcher.group(1);
            String javaFormat = liquidFormat
                    .replace("%Y", "yyyy")
                    .replace("%m", "MM")
                    .replace("%-m", "M")
                    .replace("%d", "dd")
                    .replace("%-d", "d")
                    .replace("%e", "d")
                    .replace("%H", "HH")
                    .replace("%-H", "H")
                    .replace("%k", "H")
                    .replace("%I", "hh")
                    .replace("%l", "h")
                    .replace("%M", "mm")
                    .replace("%S", "ss")
                    .replace("%p", "a")
                    .replace("%B", "MMMM")
                    .replace("%b", "MMM")
                    .replace("%A", "EEEE")
                    .replace("%a", "EEE")
                    .replace("%Z", "z")
                    .replace("%z", "Z")
                    .replace("%j", "DDD")
                    .replace("%w", "e")
                    .replace("%W", "ww");

            if (javaFormat.matches(".*%[a-zA-Z].*")) {
                conversionsApplied.add("WARNING: unrecognized date format specifiers in: " + liquidFormat);
                javaFormat = javaFormat + " /* TODO: unsupported strftime specifiers */";
            }

            dateMatcher.appendReplacement(sb, ".format('" + javaFormat + "').or('')");
        }
        dateMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted date filters");
            content = result;
        }

        // Jekyll treats 'now' as a magic date value meaning "current time".
        // In Qute/Roq, this is the `now` global variable (unquoted).
        content = content.replace("'now'.format(", "now.format(");

        return content;
    }

    private String convertDefaultFilter(String content) {
        Pattern defaultPattern = Pattern.compile("\\s*\\|\\s*default:\\s*([\"'][^\"']*[\"']|\\S+)");
        Matcher defaultMatcher = defaultPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (defaultMatcher.find()) {
            String defaultVal = defaultMatcher.group(1);
            defaultMatcher.appendReplacement(sb, " ?: " + Matcher.quoteReplacement(defaultVal));
        }
        defaultMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted default filter");
            content = result;
        }

        content = content.replaceAll("\\s{2,}\\?:", " ?:");

        return content;
    }

    private String stripDefaultBeforeSplit(String content) {
        // When | default: X | split: Y appear in sequence, drop the default filter.
        // The split is converted to a namespace call str:split() which handles null,
        // so the default is redundant. More importantly, keeping it produces
        // (expr ?: X).split(Y) which Qute's {#let} parser silently drops the .split() call.
        String result = content.replaceAll(
                "\\s*\\|\\s*default:\\s*(?:[\"'][^\"']*[\"']|\\S+)(\\s*\\|\\s*split:)",
                "$1");
        if (!result.equals(content)) {
            conversionsApplied.add("Stripped default filter before split (namespace split handles null)");
        }
        return result;
    }

    private String convertTwoArgFilters(String content) {
        // Truncatewords: | truncatewords: 50 -> .wordLimit(50)
        Pattern truncatePattern = Pattern.compile("\\s*\\|\\s*truncatewords:\\s*(\\d+)");
        String result = truncatePattern.matcher(content).replaceAll(".wordLimit($1)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted truncate filter");
            content = result;
        }

        // Replace_regex (must be before replace to avoid partial match)
        // Also convert Ruby backreferences (\1, \2) to Java ($1, $2)
        // \\s* before \\| consumes whitespace so .replaceAll attaches directly to the expression
        Pattern replaceRegexPattern = Pattern
                .compile("\\s*\\|\\s*replace_regex:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        Matcher replaceRegexMatcher = replaceRegexPattern.matcher(content);
        StringBuilder replaceRegexSb = new StringBuilder();
        boolean replaceRegexChanged = false;
        while (replaceRegexMatcher.find()) {
            String pattern_arg = replaceRegexMatcher.group(1);
            String replacement_arg = replaceRegexMatcher.group(2);
            // Convert \1, \2, etc. to $1, $2 in the replacement string
            replacement_arg = replacement_arg.replaceAll("\\\\(\\d)", "\\$$1");
            replaceRegexMatcher.appendReplacement(replaceRegexSb,
                    Matcher.quoteReplacement(".replaceAll(" + pattern_arg + ", " + replacement_arg + ")"));
            replaceRegexChanged = true;
        }
        replaceRegexMatcher.appendTail(replaceRegexSb);
        if (replaceRegexChanged) {
            conversionsApplied.add("Converted replace_regex filter");
            content = replaceRegexSb.toString();
        }

        // Replace (second arg can be quoted string or variable reference)
        Pattern replacePattern = Pattern.compile("\\s*\\|\\s*replace:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"]|\\w+)");
        result = replacePattern.matcher(content).replaceAll(".replace($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted replace filter");
            content = result;
        }

        // Where: | where: "key", "value" -> .where("key", "value")
        Pattern wherePattern = Pattern.compile("\\s*\\|\\s*where:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = wherePattern.matcher(content).replaceAll(".where($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted where filter");
            content = result;
        }

        return content;
    }

    private String convertPushFilter(String content) {
        Pattern pushPattern = Pattern.compile("\\s*\\|\\s*push:\\s*([^}|%]+)");
        Matcher pushMatcher = pushPattern.matcher(content);
        StringBuilder pushSb = new StringBuilder();

        while (pushMatcher.find()) {
            String param = pushMatcher.group(1).trim();
            pushMatcher.appendReplacement(pushSb, ".push(" + param + ")");
        }
        pushMatcher.appendTail(pushSb);

        String result = pushSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted push filter");
            content = result;
        }

        return content;
    }

    private int splitDelimCounter = 0;

    private String convertSplitFilter(String content) {
        // Use namespace form str:split(base, delim) instead of base.split(delim).
        // Namespace extensions receive null as a regular parameter, so they handle
        // null base objects that instance extensions can't dispatch on.
        // Delimiter regex: match 'content' or "content" where content can include the opposite quote
        Pattern splitPattern = Pattern.compile(
                "([a-zA-Z0-9_\\.\"'\\[\\]()]+)\\s*\\|\\s*split:\\s*('[^']*'|\"[^\"]*\")");
        Matcher m = splitPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (m.find()) {
            String base = m.group(1);
            String delim = m.group(2);
            String delimInner = delim.substring(1, delim.length() - 1);
            if (delimInner.contains(")") || delimInner.contains("\"")) {
                // Qute's parser treats ) and " inside method arguments as special,
                // even when inside a single-quoted string literal.
                // Hoist the delimiter to a variable name — the actual {#let} is
                // emitted by convertAssignments or the expression context.
                String varName = "__delim" + (splitDelimCounter++ == 0 ? "" : String.valueOf(splitDelimCounter));
                splitDelimHoists.put(varName, delim);
                m.appendReplacement(sb, Matcher.quoteReplacement("str:split(" + base + ", " + varName + ")"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement("str:split(" + base + ", " + delim + ")"));
            }
            found = true;
        }
        m.appendTail(sb);
        if (found) {
            conversionsApplied.add("Converted split filter");
            return sb.toString();
        }
        return content;
    }

    private String wrapHoistedSplitDelimiters(String content) {
        // Find output expressions referencing hoisted delimiters that weren't
        // already handled by convertAssignments (i.e. bare {{ x | split: ... }})
        for (Map.Entry<String, String> entry : splitDelimHoists.entrySet()) {
            String varName = entry.getKey();
            String delim = entry.getValue();
            // Match {= ... __delim ... } that is NOT already inside a {#let __delim=...}
            String marker = varName;
            int idx = content.indexOf(marker);
            while (idx >= 0) {
                // Check if this occurrence is already inside a {#let __delim=...} declaration
                String before = content.substring(Math.max(0, idx - 200), idx);
                if (!before.contains("{#let " + varName + "=")) {
                    // Find the enclosing {= ... } expression
                    int exprStart = content.lastIndexOf("{=", idx);
                    int exprEnd = content.indexOf("}", idx);
                    if (exprStart >= 0 && exprEnd >= 0) {
                        content = content.substring(0, exprStart)
                                + "{#let " + varName + "=" + delim + "}"
                                + content.substring(exprStart, exprEnd + 1)
                                + "{/let}"
                                + content.substring(exprEnd + 1);
                    }
                }
                idx = content.indexOf(marker, idx + marker.length() + 100);
            }
        }
        return content;
    }

    private String convertConditionals(String content) {
        String original = content;

        // if statements
        content = content.replaceAll("\\{%\\s*if\\s+([^%]+?)\\s*%\\}", "{#if $1}");
        content = content.replaceAll("\\{%\\s*elsif\\s+([^%]+?)\\s*%\\}", "{#else if $1}");
        content = content.replaceAll("\\{%\\s*else\\s*%\\}", "{#else}");
        content = content.replaceAll("\\{%\\s*endif\\s*%\\}", "{/if}");

        // unless (negative if) — apply De Morgan's law to avoid !(...)
        content = convertUnless(content);
        content = content.replaceAll("\\{%\\s*endunless\\s*%\\}", "{/if}");

        // Convert operators ONLY inside conditional blocks to avoid corrupting prose text
        Pattern ifPattern = Pattern.compile("(\\{#(?:if|else if)\\s+)([^}]+?)(\\})");
        Matcher ifMatcher = ifPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (ifMatcher.find()) {
            String prefix = ifMatcher.group(1);
            String condition = ifMatcher.group(2);
            String suffix = ifMatcher.group(3);

            condition = replaceOperatorsOutsideStrings(condition);
            condition = replaceNilWithNull(condition);

            ifMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + condition + suffix));
        }
        ifMatcher.appendTail(sb);
        content = sb.toString();

        if (!content.equals(original)) {
            conversionsApplied.add("Converted conditionals");
        }
        return content;
    }

    private String convertUnless(String content) {
        Pattern p = Pattern.compile("\\{%\\s*unless\\s+([^%]+?)\\s*%\\}");
        Matcher m = p.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String cond = m.group(1).trim();
            String negated = negateLiquidCondition(cond);
            m.appendReplacement(sb, Matcher.quoteReplacement("{#if " + negated + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String negateLiquidCondition(String condition) {
        // Split on " or " (Liquid's OR) and negate each part, joining with " and "
        // De Morgan: !(A or B or C) => !A and !B and !C
        String[] orParts = condition.split("\\s+or\\s+");
        if (orParts.length > 1) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < orParts.length; i++) {
                if (i > 0)
                    result.append(" and ");
                result.append(negateSingleCondition(orParts[i].trim()));
            }
            return result.toString();
        }
        // Split on " and " and negate each part, joining with " or "
        // De Morgan: !(A and B) => !A or !B
        String[] andParts = condition.split("\\s+and\\s+");
        if (andParts.length > 1) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < andParts.length; i++) {
                if (i > 0)
                    result.append(" or ");
                result.append(negateSingleCondition(andParts[i].trim()));
            }
            return result.toString();
        }
        return negateSingleCondition(condition.trim());
    }

    private String negateSingleCondition(String cond) {
        // If already negated, remove the negation (double negative)
        if (cond.startsWith("!")) {
            return cond.substring(1);
        }
        // If it contains a comparison operator, flip it
        if (cond.contains(" != ")) {
            return cond.replace(" != ", " == ");
        }
        if (cond.contains("!=")) {
            return cond.replace("!=", " == ");
        }
        if (cond.contains(" == ")) {
            return cond.replace(" == ", " != ");
        }
        if (cond.contains("==")) {
            return cond.replace("==", " != ");
        }
        if (cond.contains(" >= ")) {
            return cond.replace(" >= ", " < ");
        }
        if (cond.contains(" <= ")) {
            return cond.replace(" <= ", " > ");
        }
        if (cond.contains(" > ")) {
            return cond.replace(" > ", " <= ");
        }
        if (cond.contains(" < ")) {
            return cond.replace(" < ", " >= ");
        }
        // Simple variable — prefix with !
        return "!" + cond;
    }

    private String replaceOperatorsOutsideStrings(String condition) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int i = 0;

        while (i < condition.length()) {
            if (!inSingleQuote && !inDoubleQuote) {
                if (condition.charAt(i) == '"') {
                    inDoubleQuote = true;
                    result.append('"');
                    i++;
                } else if (condition.charAt(i) == '\'') {
                    inSingleQuote = true;
                    result.append('\'');
                    i++;
                } else if (matchesWord(condition, i, "and")) {
                    result.append("&&");
                    i += 3;
                } else if (matchesWord(condition, i, "or")) {
                    result.append("||");
                    i += 2;
                } else if (i + 1 < condition.length()
                        && (condition.substring(i, i + 2).equals("!=")
                                || condition.substring(i, i + 2).equals("=="))) {
                    // Qute uses 'ne' for !=, '==' stays as-is but needs spaces
                    String op = condition.substring(i, i + 2);
                    String quteOp = op.equals("!=") ? "ne" : op;
                    String before = result.toString();
                    if (!before.isEmpty() && before.charAt(before.length() - 1) != ' ') {
                        result.append(' ');
                    }
                    result.append(quteOp);
                    i += 2;
                    if (i < condition.length() && condition.charAt(i) != ' ') {
                        result.append(' ');
                    }
                } else if (matchesWord(condition, i, "contains")) {
                    // Convert operator to method call: X contains 'Y' → X.contains('Y')
                    // Walk backwards to find the base expression
                    String before = result.toString().stripTrailing();
                    result.setLength(0);
                    result.append(before);
                    result.append(".contains(");
                    i += "contains".length();
                    // Skip whitespace after "contains"
                    while (i < condition.length() && condition.charAt(i) == ' ')
                        i++;
                    // Find the argument (quoted string or expression)
                    int argStart = i;
                    if (i < condition.length() && (condition.charAt(i) == '\'' || condition.charAt(i) == '"')) {
                        char quote = condition.charAt(i);
                        i++;
                        while (i < condition.length() && condition.charAt(i) != quote)
                            i++;
                        if (i < condition.length())
                            i++; // consume closing quote
                    } else {
                        while (i < condition.length() && condition.charAt(i) != ' '
                                && condition.charAt(i) != ')')
                            i++;
                    }
                    result.append(condition, argStart, i);
                    result.append(")");
                } else {
                    result.append(condition.charAt(i));
                    i++;
                }
            } else {
                if (inDoubleQuote && condition.charAt(i) == '"') {
                    inDoubleQuote = false;
                } else if (inSingleQuote && condition.charAt(i) == '\'') {
                    inSingleQuote = false;
                }
                result.append(condition.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    private String replaceNilWithNull(String condition) {
        return condition.replaceAll("\\bnil\\b", "null");
    }

    private static boolean matchesWord(String s, int pos, String word) {
        if (pos + word.length() > s.length()) {
            return false;
        }
        if (!s.substring(pos, pos + word.length()).equals(word)) {
            return false;
        }
        if (pos > 0 && Character.isLetterOrDigit(s.charAt(pos - 1))) {
            return false;
        }
        if (pos + word.length() < s.length() && Character.isLetterOrDigit(s.charAt(pos + word.length()))) {
            return false;
        }
        return true;
    }

    private String convertFindFirstPattern(String content) {
        // Detect the Liquid "find first" idiom:
        //   {% assign X = nil %}
        //   {% for item in list %}{% unless item.prop %}{% unless X %}
        //     {% assign X = item.field %}
        //   {% endunless %}{% endunless %}{% endfor %}
        //
        // Qute's {#let} is block-scoped, so assigns inside a loop are invisible outside.
        // Convert to: {#let __firstMatch=list:whereNot(list, 'prop').first}
        //             {#let X=__firstMatch.field}
        String original = content;

        Pattern pattern = Pattern.compile(
                "\\{%\\s*assign\\s+(\\w+)\\s*=\\s*nil\\s*%\\}" + // 1: guard var = nil
                        "\\s*\\{%\\s*for\\s+(\\w+)\\s+in\\s+(.+?)\\s*%\\}" + // 2: loop var, 3: list
                        "\\s*\\{%\\s*unless\\s+\\2\\.(\\w+)\\s*%\\}" + // 4: filter property
                        "\\s*\\{%\\s*unless\\s+\\1\\s*%\\}" + // guard check
                        "((?:\\s*\\{%\\s*assign\\s+\\w+\\s*=\\s*.+?\\s*%\\})+)" + // 5: assign block
                        "\\s*\\{%\\s*endunless\\s*%\\}" +
                        "\\s*\\{%\\s*endunless\\s*%\\}" +
                        "\\s*\\{%\\s*endfor\\s*%\\}",
                Pattern.DOTALL);

        Matcher m = pattern.matcher(content);
        if (m.find()) {
            String guardVar = m.group(1);
            String loopVar = m.group(2);
            String list = m.group(3);
            String filterProp = m.group(4);
            String assignBlock = m.group(5);

            // Extract individual assigns and replace loop-var references with __firstMatch
            Pattern assignPat = Pattern.compile("\\{%\\s*assign\\s+(\\w+)\\s*=\\s*(.+?)\\s*%\\}");
            Matcher am = assignPat.matcher(assignBlock);
            StringBuilder lets = new StringBuilder();
            while (am.find()) {
                String var = am.group(1);
                String expr = am.group(2).replaceAll("\\b" + Pattern.quote(loopVar) + "\\b", "__firstMatch");
                lets.append("{% assign ").append(var).append(" = ").append(expr).append(" %}");
            }

            String replacement = "{#let __firstMatch=list:whereNot(" + list
                    + ", '" + filterProp + "').first}"
                    + lets;

            content = content.substring(0, m.start()) + replacement + content.substring(m.end());
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Converted find-first loop to list:whereNot");
        }
        return content;
    }

    private String collapseWhereExpAccumulatorLoop(String content) {
        // Detect iterative where_exp filtering inside a for-loop:
        //   {% for query in QUERIES %}
        //     {% assign VAR = VAR | where_exp: "LOOPVAR", query %}
        //   {% endfor %}
        // Collapse to: {% assign VAR = list:whereExp(VAR, "LOOPVAR", QUERIES) %}
        Pattern pattern = Pattern.compile(
                "\\{%\\s*for\\s+(\\w+)\\s+in\\s+(.+?)\\s*%\\}" + // 1: loop var, 2: queries list
                        "\\s*\\{%\\s*assign\\s+(\\w+)\\s*=\\s*\\3\\s*\\|\\s*" + // 3: accumulator var (same on both sides)
                        "where_exp:\\s*(\"[^\"]*\"|'[^']*')\\s*,\\s*\\1\\s*%\\}" + // 4: loopVar name, then loop var ref as expr
                        "\\s*\\{%\\s*endfor\\s*%\\}",
                Pattern.DOTALL);

        Matcher m = pattern.matcher(content);
        if (m.find()) {
            String queriesList = m.group(2).trim();
            String accumulatorVar = m.group(3);
            String loopVarName = m.group(4);

            String replacement = "{% assign " + accumulatorVar + " = list:whereExp("
                    + accumulatorVar + ", " + loopVarName + ", " + queriesList + ") %}";

            content = content.substring(0, m.start()) + replacement + content.substring(m.end());
            conversionsApplied.add("Collapsed where_exp accumulator loop");
        }
        return content;
    }

    private String convertPaginator(String content) {
        String original = content;

        // Jekyll autopages (paginate-v2) inject page.pagination.{entity} — detect this
        // BEFORE converting paginator refs, since convertCustomPageFields hasn't run yet.
        // Autopages paginator.posts is pre-filtered by entity; Roq from-data pages have
        // no paginator, so use .filter() on the collection instead.
        String autopagesEntity = detectAutopagesEntity(content);
        if (autopagesEntity != null) {
            content = content.replaceAll("\\bpaginator\\.posts\\b",
                    "site.collections.get('posts').filter('" + autopagesEntity
                            + "', page.pagination." + autopagesEntity + ")");
        } else {
            content = content.replaceAll("\\bpaginator\\.posts\\b",
                    "site.collections.get('posts').paginated(page.paginator)");
        }

        // Jekyll field names -> Roq Paginator field names
        content = content.replaceAll("\\bpaginator\\.total_pages\\b", "page.paginator.total");
        content = content.replaceAll("\\bpaginator\\.next_page_path\\b", "page.paginator.next");
        content = content.replaceAll("\\bpaginator\\.previous_page_path\\b", "page.paginator.previous");
        content = content.replaceAll("\\bpaginator\\.next_page\\b", "page.paginator.next");
        content = content.replaceAll("\\bpaginator\\.previous_page\\b", "page.paginator.previous");

        // Any remaining paginator.X -> page.paginator.X
        content = content.replaceAll("(?<!page\\.)\\bpaginator\\.", "page.paginator.");

        // Guard conditionals: page.paginator is null on non-paginated pages
        Pattern pattern = Pattern.compile("(\\{#(?:if|else if) )(?!page\\.paginator &&)(page\\.paginator\\.[^}]+\\})");
        content = pattern.matcher(content).replaceAll("$1page.paginator && $2");

        // Also guard conditionals using paginated(page.paginator) — e.g. .size > 0 comparisons
        Pattern paginatedGuard = Pattern.compile(
                "(\\{#(?:if|else if) )(?!page\\.paginator &&)(.*?paginated\\(page\\.paginator\\).*?\\})");
        content = paginatedGuard.matcher(content).replaceAll("$1page.paginator && $2");

        if (!content.equals(original)) {
            conversionsApplied.add("Converted Jekyll paginator to Roq pagination");
        }
        return content;
    }

    private String detectAutopagesEntity(String content) {
        Matcher m = Pattern.compile("page\\.pagination\\.(\\w+)\\b").matcher(content);
        while (m.find()) {
            if (!m.group(1).endsWith("_data")) {
                return m.group(1);
            }
        }
        return null;
    }

    private String convertLoops(String content) {
        String original = content;

        // Basic for loop
        content = content.replaceAll("\\{%\\s*for\\s+(\\w+)\\s+in\\s+([^%]+?)\\s*%\\}", "{#for $1 in $2}");
        content = content.replaceAll("\\{%\\s*endfor\\s*%\\}", "{/for}");

        // Append .orEmpty to property-access iterables (e.g., post.tags) that might be null
        content = appendOrEmptyToForLoops(content);

        // Loop variables: replace forloop.* with Qute metadata derived from the actual loop variable name.
        // We find each {#for VAR in ...} and replace forloop.* references with VAR_* in the loop body.
        content = replaceLoopVariables(content);

        // Handle limit and offset: wrap loop body with a count guard
        // since Roq collections don't have .limit()/.skip() methods.
        // {#for x in list limit: N} → {#for x in list}{#if x_count <= N}...{/if}{/for}
        Pattern limitPattern = Pattern
                .compile("\\{#for\\s+(\\w+)\\s+in\\s+([^}]+?)\\s+limit:\\s*(\\w+)(?:\\s+offset:\\s*(\\w+))?\\s*\\}");
        Matcher limitMatcher = limitPattern.matcher(content);
        while (limitMatcher.find()) {
            String var = limitMatcher.group(1);
            String collection = limitMatcher.group(2);
            String limit = limitMatcher.group(3);
            String offset = limitMatcher.group(4);

            int forEnd = limitMatcher.end();
            int endForPos = findMatchingEndFor(content, forEnd);
            if (endForPos < 0)
                continue;

            String loopBody = content.substring(forEnd, endForPos);
            String forOpen;
            if (offset != null && !offset.equals("0")) {
                forOpen = "{#for " + var + " in " + collection + "}"
                        + "{#if " + var + "_count > " + offset + " && " + var + "_count <= " + offset + " + " + limit + "}";
            } else {
                forOpen = "{#for " + var + " in " + collection + "}"
                        + "{#if " + var + "_count <= " + limit + "}";
            }

            content = content.substring(0, limitMatcher.start())
                    + forOpen + loopBody + "{/if}{/for}"
                    + content.substring(endForPos + "{/for}".length());
            limitMatcher = limitPattern.matcher(content);
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Converted loops");
        }
        return content;
    }

    private String appendOrEmptyToForLoops(String content) {
        // Iterables in for loops might be null/not-found at runtime (e.g. post.tags,
        // or {#let}-bound variables whose expression failed to resolve).
        // Append .orEmpty so Qute returns an empty list instead of throwing.
        // Skip cdi: references (always defined) and already-safe expressions.
        Pattern p = Pattern.compile("(\\{#for\\s+\\w+\\s+in\\s+)([^\\s}]+)(\\s[^}]*\\}|\\})");
        Matcher m = p.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String prefix = m.group(1);
            String iterable = m.group(2).trim();
            String suffix = m.group(3);
            if (!iterable.startsWith("cdi:") && !iterable.endsWith(".orEmpty")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(prefix + iterable + ".orEmpty" + suffix));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String replaceLoopVariables(String content) {
        // Find each for loop and replace forloop.* within its body
        Pattern forPattern = Pattern.compile("\\{#for\\s+(\\w+)\\s+in\\s+[^}]+\\}");
        Matcher forMatcher = forPattern.matcher(content);

        // Collect loop variable names and their positions
        List<int[]> loopRanges = new ArrayList<>();
        List<String> loopVars = new ArrayList<>();

        while (forMatcher.find()) {
            loopVars.add(forMatcher.group(1));
            loopRanges.add(new int[] { forMatcher.end(), findMatchingEndFor(content, forMatcher.end()) });
        }

        // Process from last to first to preserve positions
        for (int i = loopRanges.size() - 1; i >= 0; i--) {
            String var = loopVars.get(i);
            int start = loopRanges.get(i)[0];
            int end = loopRanges.get(i)[1];
            if (end <= start) {
                continue;
            }

            String body = content.substring(start, end);
            body = body.replaceAll("forloop\\.index0", var + "_index");
            body = body.replaceAll("forloop\\.index", var + "_count");
            body = body.replaceAll("forloop\\.first", var + "_count == 1");
            body = body.replaceAll("forloop\\.last", "!" + var + "_hasNext");
            content = content.substring(0, start) + body + content.substring(end);
        }

        return content;
    }

    private int findMatchingEndFor(String content, int startPos) {
        int depth = 1;
        Pattern tagPattern = Pattern.compile("\\{#for\\s|\\{/for\\}");
        Matcher matcher = tagPattern.matcher(content);
        matcher.region(startPos, content.length());

        while (matcher.find()) {
            if (matcher.group().startsWith("{#for")) {
                depth++;
            } else {
                depth--;
                if (depth == 0) {
                    return matcher.start();
                }
            }
        }
        return content.length();
    }

    private String convertIncludes(String content) {
        // Liquid: {% include "file.html" %}
        // Qute: {#include file.html /}
        Pattern includePattern = Pattern.compile("\\{%\\s*include\\s+[\"']?([^\"'%\\s]+)[\"']?\\s*([^%]*?)\\s*%\\}");
        Matcher includeMatcher = includePattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (includeMatcher.find()) {
            String file = includeMatcher.group(1);
            if (file.startsWith("/")) {
                file = file.substring(1);
            }
            String params = includeMatcher.group(2).trim();

            // Roq resolves includes from templates/ directory;
            // partials go under templates/partials/
            String path = file.startsWith("partials/") ? file : "partials/" + file;

            String replacement;
            if (!params.isEmpty()) {
                replacement = "{#include " + path + " " + params + " _unisolated /}";
            } else {
                replacement = "{#include " + path + " _unisolated /}";
            }

            includeMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        includeMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted includes");
        }

        return result;
    }

    private String convertIncludeParamAccess(String content) {
        String result = content.replaceAll("\\binclude\\.", "");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted include.param references to direct param access");
        }
        return result;
    }

    private record IfElseBlock(int start, int end, String condition, String ifBranch, String elseBranch) {
    }

    private record IfBlock(int start, int end, String condition, String body) {
    }

    private List<IfElseBlock> findIfElseBlocks(String content) {
        List<IfElseBlock> blocks = new ArrayList<>();
        Pattern ifOpenPattern = Pattern.compile("\\{#if\\s+([^}]+?)\\}");
        Matcher ifMatcher = ifOpenPattern.matcher(content);

        while (ifMatcher.find()) {
            int blockStart = ifMatcher.start();
            String condition = ifMatcher.group(1);
            int pos = ifMatcher.end();
            int depth = 0;
            int elsePos = -1;

            Pattern innerTag = Pattern.compile("\\{#if\\b[^}]*\\}|\\{#else\\}|\\{/if\\}");
            Matcher inner = innerTag.matcher(content);
            inner.region(pos, content.length());

            while (inner.find()) {
                String t = inner.group();
                if (t.startsWith("{#if")) {
                    depth++;
                } else if (t.equals("{#else}") && depth == 0) {
                    elsePos = inner.start();
                } else if (t.equals("{/if}")) {
                    if (depth == 0) {
                        if (elsePos >= 0) {
                            String ifBranch = content.substring(pos, elsePos);
                            String elseBranch = content.substring(elsePos + "{#else}".length(), inner.start());
                            blocks.add(new IfElseBlock(blockStart, inner.end(), condition, ifBranch, elseBranch));
                        }
                        break;
                    }
                    depth--;
                }
            }
        }
        return blocks;
    }

    private String mergeComplementaryIfBlocks(String content) {
        // Liquid's {% assign %} is template-scoped: a variable assigned inside an
        // {% if %} block is visible after {% endif %}. Qute's {#let} is block-scoped:
        // the variable dies at {/let}, which convertAssignments places at the enclosing
        // {/if}. So {% if X %}{% assign V = A %}{% endif %}{% unless X %}{% assign V = B %}
        // {% endunless %}{{ V }} works in Liquid but breaks in Qute — V is unset by the
        // time we reach the output expression.
        //
        // Fix: detect consecutive {#if COND}...{/if}{#if !COND}...{/if} pairs and merge
        // them into {#if COND}...{#else}...{/if} so convertIfElseAssignsToTernary can
        // hoist the assign out of the conditional entirely.
        String original = content;
        List<IfBlock> blocks = collectIfWithoutElseBlocks(content);

        // Find consecutive pairs where the second condition negates the first.
        // After each merge, positions shift, so break and re-scan from scratch.
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < blocks.size() - 1; i++) {
                IfBlock first = blocks.get(i);
                IfBlock second = blocks.get(i + 1);

                String between = content.substring(first.end(), second.start());
                if (!between.trim().isEmpty()) {
                    continue;
                }

                if (areComplementary(first.condition(), second.condition())) {
                    content = content.substring(0, first.end() - "{/if}".length())
                            + "{#else}"
                            + second.body()
                            + "{/if}"
                            + content.substring(second.end());
                    merged = true;
                    break; // re-scan with fresh positions
                }
            }
            if (merged) {
                // Re-collect blocks from the modified content
                blocks = collectIfWithoutElseBlocks(content);
            }
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Merged complementary if/unless blocks");
        }
        return content;
    }

    private boolean areComplementary(String condA, String condB) {
        condA = condA.trim();
        condB = condB.trim();
        // {#if X} + {#if !X}
        if (condB.equals("!" + condA))
            return true;
        if (condA.equals("!" + condB))
            return true;
        return false;
    }

    private List<IfBlock> collectIfWithoutElseBlocks(String content) {
        List<IfBlock> blocks = new ArrayList<>();
        Pattern ifOpenPattern = Pattern.compile("\\{#if\\s+([^}]+?)\\}");
        Matcher ifMatcher = ifOpenPattern.matcher(content);

        while (ifMatcher.find()) {
            int blockStart = ifMatcher.start();
            String condition = ifMatcher.group(1);
            int pos = ifMatcher.end();
            int depth = 0;
            boolean hasElse = false;
            int blockEnd = -1;

            Pattern innerTag = Pattern.compile("\\{#if\\b[^}]*\\}|\\{#else\\}|\\{/if\\}");
            Matcher inner = innerTag.matcher(content);
            inner.region(pos, content.length());

            while (inner.find()) {
                String t = inner.group();
                if (t.startsWith("{#if")) {
                    depth++;
                } else if (t.equals("{#else}") && depth == 0) {
                    hasElse = true;
                } else if (t.equals("{/if}")) {
                    if (depth == 0) {
                        blockEnd = inner.end();
                        if (!hasElse) {
                            String body = content.substring(pos, inner.start());
                            blocks.add(new IfBlock(blockStart, blockEnd, condition, body));
                        }
                        break;
                    }
                    depth--;
                }
            }
            // Skip past this block to avoid collecting nested {#if} tags
            if (blockEnd > 0) {
                ifMatcher.region(blockEnd, content.length());
            }
        }
        return blocks;
    }

    private String convertIfElseAssignsToTernary(String content) {
        // Detect if/else blocks where the same variable is assigned in both branches.
        // Replace both assigns with a single ternary assign BEFORE the if block.
        // The result is still a {% assign %} tag, so convertAssignments handles scoping.

        List<IfElseBlock> blocks = findIfElseBlocks(content);
        boolean changed = false;

        // Process from last to first so positions stay valid
        for (int bi = blocks.size() - 1; bi >= 0; bi--) {
            IfElseBlock block = blocks.get(bi);
            String condition = block.condition();
            String ifBranch = block.ifBranch();
            String elseBranch = block.elseBranch();

            Pattern assignPattern = Pattern.compile("\\{%\\s*assign\\s+(\\w+)\\s*=\\s*([^%]+?)\\s*%\\}");
            Matcher ifAssigns = assignPattern.matcher(ifBranch);
            Matcher elseAssigns = assignPattern.matcher(elseBranch);

            Map<String, String> ifVars = new HashMap<>();
            Map<String, String> elseVars = new HashMap<>();

            while (ifAssigns.find()) {
                ifVars.put(ifAssigns.group(1), ifAssigns.group(2));
            }
            while (elseAssigns.find()) {
                elseVars.put(elseAssigns.group(1), elseAssigns.group(2));
            }

            Set<String> commonVars = new HashSet<>(ifVars.keySet());
            commonVars.retainAll(elseVars.keySet());

            if (!commonVars.isEmpty()) {
                StringBuilder ternaryAssigns = new StringBuilder();
                String modifiedIfBranch = ifBranch;
                String modifiedElseBranch = elseBranch;

                for (String var : commonVars) {
                    String ifExpr = ifVars.get(var);
                    String elseExpr = elseVars.get(var);

                    String combinedExpr;
                    String condTrimmed = condition.trim();
                    String ifTrimmed = ifExpr.trim();
                    String elseTrimmed = elseExpr.trim();

                    if (condTrimmed.equals(ifTrimmed)
                            && (elseTrimmed.equals("false") || elseTrimmed.equals("nil"))) {
                        // if X → assign V = X | else → assign V = false
                        // Just use the condition directly; null is falsy like false.
                        combinedExpr = ifTrimmed;
                    } else if ((elseTrimmed.equals("false") || elseTrimmed.equals("nil"))
                            && ifTrimmed.startsWith(condTrimmed + ".")) {
                        // if X → assign V = X.method() | else → assign V = false
                        // Use the direct method call; Qute non-strict mode handles null.
                        combinedExpr = ifTrimmed;
                    } else {
                        // General case: Qute does NOT support ternary (? :) in {#let} parameters.
                        // Keep the if/else structure and pull ALL remaining content into both
                        // branches so the scoped {#let} variable is visible where it's used.
                        combinedExpr = null;
                    }

                    if (combinedExpr != null) {
                        ternaryAssigns.append("{% assign ").append(var).append(" = ")
                                .append(combinedExpr).append(" %}\n");

                        modifiedIfBranch = modifiedIfBranch.replaceFirst(
                                "\\{%\\s*assign\\s+" + var + "\\s*=\\s*" + Pattern.quote(ifExpr) + "\\s*%\\}",
                                "");
                        modifiedElseBranch = modifiedElseBranch.replaceFirst(
                                "\\{%\\s*assign\\s+" + var + "\\s*=\\s*" + Pattern.quote(elseExpr) + "\\s*%\\}",
                                "");
                    } else {
                        String trailing = content.substring(block.end());
                        int scopeEnd = findEnclosingScopeEnd(trailing);
                        String trailingInScope = trailing.substring(0, scopeEnd);
                        String afterScope = trailing.substring(scopeEnd);

                        if (!trailingInScope.trim().isEmpty()) {
                            String replacement = "{#if " + condition + "}"
                                    + ifBranch + trailingInScope
                                    + "{#else}"
                                    + elseBranch + trailingInScope
                                    + "{/if}"
                                    + afterScope;

                            content = content.substring(0, block.start()) + replacement;
                            changed = true;
                            break; // Re-parse since positions shifted
                        }
                    }
                }

                if (ternaryAssigns.length() == 0) {
                    continue;
                }

                // Check if the if/else block is now empty (only whitespace)
                boolean ifEmpty = modifiedIfBranch.trim().isEmpty();
                boolean elseEmpty = modifiedElseBranch.trim().isEmpty();

                String replacement;
                if (ifEmpty && elseEmpty) {
                    replacement = ternaryAssigns.toString();
                } else {
                    replacement = ternaryAssigns.toString()
                            + "{#if " + condition + "}"
                            + modifiedIfBranch
                            + "{#else}"
                            + modifiedElseBranch
                            + "{/if}";
                }

                content = content.substring(0, block.start())
                        + replacement
                        + content.substring(block.end());
                changed = true;
            }
        }

        if (changed) {
            conversionsApplied.add("Converted if/else assigns");
        }

        return content;
    }

    private int findEnclosingScopeEnd(String text) {
        // Find the end of the enclosing scope — the position of the first unmatched
        // closing tag ({/if}, {/for}, {/let}, etc.) or end of text.
        // This ensures we only duplicate content within the same structural block.
        Pattern tag = Pattern.compile("\\{#(?:if|for|let|set)\\b[^}]*\\}|\\{/(?:if|for|let|set)\\}|\\{#else\\}");
        Matcher m = tag.matcher(text);
        int depth = 0;
        while (m.find()) {
            String t = m.group();
            if (t.startsWith("{#if") || t.startsWith("{#for") || t.startsWith("{#let") || t.startsWith("{#set")) {
                depth++;
            } else if (t.startsWith("{/")) {
                if (depth == 0) {
                    return m.start();
                }
                depth--;
            } else if (t.equals("{#else}") && depth == 0) {
                return m.start();
            }
        }
        return text.length();
    }

    private String convertMutableAssigns(String content) {
        String original = content;

        Pattern assignPattern = Pattern.compile("\\{%\\s*assign\\s+(\\w+)\\s*=\\s*([^%]+?)\\s*%\\}");
        Matcher m = assignPattern.matcher(content);

        List<int[]> positions = new ArrayList<>();
        List<String> varNames = new ArrayList<>();
        List<String> expressions = new ArrayList<>();

        while (m.find()) {
            positions.add(new int[] { m.start(), m.end() });
            varNames.add(m.group(1));
            expressions.add(m.group(2).trim());
        }

        if (positions.isEmpty()) {
            return content;
        }

        // Group assigns by variable name
        Map<String, List<Integer>> assignsByVar = new LinkedHashMap<>();
        for (int i = 0; i < varNames.size(); i++) {
            assignsByVar.computeIfAbsent(varNames.get(i), k -> new ArrayList<>()).add(i);
        }

        // A variable needs mutable treatment when it would escape its {#let} scope.
        // Exclude patterns already handled by dedicated passes:
        //  - self-referencing assigns (push/accumulation → collapsePushInLoopPattern)
        //  - if/else complementary assigns (→ convertIfElseAssignsToTernary)
        Set<String> mutableVars = new LinkedHashSet<>();

        for (var entry : assignsByVar.entrySet()) {
            String var = entry.getKey();
            List<Integer> indices = entry.getValue();

            // Skip self-referencing assigns (e.g. values = values | push: item).
            // These are accumulation patterns handled by collapsePushInLoop / mergeTypes.
            boolean selfRef = false;
            for (int idx : indices) {
                if (expressions.get(idx).matches(".*\\b" + Pattern.quote(var) + "\\b.*")) {
                    selfRef = true;
                    break;
                }
            }
            if (selfRef) {
                continue;
            }

            if (indices.size() > 1) {
                // Flag when assigns are at different nesting depths — that's
                // the "default + conditional override" pattern (e.g. assign false at
                // loop level, assign true inside nested if).
                Set<Integer> depths = new HashSet<>();
                for (int idx : indices) {
                    depths.add(nestingDepth(content, positions.get(idx)[0]));
                }
                if (depths.size() > 1) {
                    mutableVars.add(var);
                    continue;
                }
                // Same-depth assigns are typically if/else alternatives handled by
                // convertIfElseAssignsToTernary — but still need mutable treatment
                // if the variable is used outside the enclosing block.
                int lastAssignEnd = positions.get(indices.get(indices.size() - 1))[1];
                int scopeEnd = findEnclosingBlockEnd(content, lastAssignEnd);
                if (scopeEnd < content.length()) {
                    String afterScope = content.substring(scopeEnd);
                    Pattern varRef = Pattern.compile("(?<![.\\w'\"])" + Pattern.quote(var) + "\\b");
                    if (varRef.matcher(afterScope).find()) {
                        mutableVars.add(var);
                    }
                }
            } else {
                // Single assign: check if the variable is used after its scope boundary
                int assignEnd = positions.get(indices.get(0))[1];
                int scopeEnd = findScopeBoundary(content, assignEnd);
                if (scopeEnd < content.length()) {
                    String afterScope = content.substring(scopeEnd);
                    // If a {#for VAR in ...} appears after the scope boundary, it
                    // rebinds the variable name as a loop variable — all references
                    // from that point on are the loop var, not the assigned one.
                    // Strip from the first rebinding to end before checking for refs.
                    Pattern forRebind = Pattern.compile("\\{#for\\s+" + Pattern.quote(var) + "\\s+in\\b.*", Pattern.DOTALL);
                    String withoutForRebinds = forRebind.matcher(afterScope).replaceAll("");
                    Pattern varRef = Pattern.compile("(?<![.\\w'\"])" + Pattern.quote(var) + "\\b");
                    if (varRef.matcher(withoutForRebinds).find()) {
                        mutableVars.add(var);
                    }
                }
            }
        }

        if (mutableVars.isEmpty()) {
            return content;
        }

        // Step 1: Replace {% assign VAR = EXPR %} for mutable vars with {=_m.assign('VAR', EXPR)}
        for (String var : mutableVars) {
            Pattern p = Pattern.compile("\\{%\\s*assign\\s+" + Pattern.quote(var) + "\\s*=\\s*([^%]+?)\\s*%\\}");
            Matcher matcher = p.matcher(content);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String expr = matcher.group(1).trim();
                String converted = convertTernaryToOrChain(expr);
                if (converted.equals("nil")) {
                    converted = "null";
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(
                        exprOpen + "_m.assign('" + var + "', " + converted + ")}"));
            }
            matcher.appendTail(sb);
            content = sb.toString();
        }

        // Step 2: Replace standalone references to mutable vars with _m.read('VAR')
        for (String var : mutableVars) {
            content = content.replaceAll(
                    "(?<![.\\w'\"])" + Pattern.quote(var) + "\\b(?!['\"])",
                    "_m.read('" + var + "')");
        }

        // Step 3: Wrap with mutable-map initialisation (after front matter if present)
        Pattern fmPattern = Pattern.compile("^(---\\n.*?\\n---\\n)", Pattern.DOTALL);
        Matcher fmMatcher = fmPattern.matcher(content);
        if (fmMatcher.find()) {
            content = fmMatcher.group(1) + "{#let _m=mut:map()}" + content.substring(fmMatcher.end()) + "{/let}";
        } else {
            content = "{#let _m=mut:map()}" + content + "{/let}";
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Converted mutable assigns to mut:map()");
        }
        return content;
    }

    private int nestingDepth(String content, int position) {
        Pattern tag = Pattern.compile("\\{#(if|for|let)\\b|\\{/(if|for|let)\\}");
        Matcher m = tag.matcher(content);
        m.region(0, position);
        int depth = 0;
        while (m.find()) {
            if (m.group().startsWith("{#")) {
                depth++;
            } else {
                depth--;
            }
        }
        return depth;
    }

    private String convertAssignments(String content) {
        String original = content;

        // Note: post.* is NOT converted to page.* — post may be a loop variable

        // Place {/let} at the enclosing scope boundary for each assign
        Pattern assignPattern = Pattern.compile("\\{%\\s*assign\\s+(\\w+)\\s*=\\s*([^%]+?)\\s*%\\}");
        Matcher matcher = assignPattern.matcher(content);

        List<int[]> positions = new ArrayList<>();
        List<String> varNames = new ArrayList<>();
        List<String> expressions = new ArrayList<>();

        while (matcher.find()) {
            positions.add(new int[] { matcher.start(), matcher.end() });
            varNames.add(matcher.group(1));
            expressions.add(matcher.group(2));
        }

        for (int i = positions.size() - 1; i >= 0; i--) {
            int assignStart = positions.get(i)[0];
            int assignEnd = positions.get(i)[1];
            String var = varNames.get(i);
            String expr = expressions.get(i);

            int scopeEnd = findScopeBoundary(content, assignEnd);
            // Qute's {#let} parser intercepts ?: — it cannot appear inside a
            // {#let} expression. Convert ?: chains to .or() method calls, which
            // preserve the fallback semantics and are valid in {#let}.
            String letExpr = convertTernaryToOrChain(expr);

            // If the expression references hoisted split delimiters, emit their
            // {#let} declarations as an outer wrapper so the variable is in scope.
            StringBuilder delimPrefix = new StringBuilder();
            StringBuilder delimSuffix = new StringBuilder();
            for (Map.Entry<String, String> entry : splitDelimHoists.entrySet()) {
                if (letExpr.contains(entry.getKey())) {
                    delimPrefix.append("{#let ").append(entry.getKey()).append("=").append(entry.getValue()).append("}");
                    delimSuffix.append("{/let}");
                }
            }

            content = content.substring(0, assignStart)
                    + delimPrefix
                    + "{#let " + var + "=" + letExpr + "}"
                    + content.substring(assignEnd, scopeEnd)
                    + "{/let}"
                    + delimSuffix
                    + content.substring(scopeEnd);
        }

        // Capture (multi-line assignment) — already block-scoped
        content = content.replaceAll("\\{%\\s*capture\\s+(\\w+)\\s*%\\}", "{#let $1=''}");
        content = content.replaceAll("\\{%\\s*endcapture\\s*%\\}", "{/let}");

        if (!content.equals(original)) {
            conversionsApplied.add("Converted assignments");
        }
        return content;
    }

    private static String convertTernaryToOrChain(String expr) {
        String[] parts = expr.split("\\s*\\?:\\s*");
        if (parts.length <= 1) {
            return expr;
        }
        StringBuilder sb = new StringBuilder(parts[0].trim());
        for (int i = 1; i < parts.length; i++) {
            sb.append(".or(").append(parts[i].trim()).append(")");
        }
        return sb.toString();
    }

    private String collapsePushInLoopPattern(String content) {
        // Detect the Liquid idiom: init empty list, push in loop, iterate after.
        // Qute's block-scoped {#let} makes push-in-loop silently discard results.
        // Collapse into a single str:splitTrimmed() call.
        //
        // Input pattern (after convertAssignments/convertLoops):
        //   {#let RAW=str:split(EXPR)}
        //   {#let CLEAN=str:split("", "")}
        //   {#for V in RAW.orEmpty}...CLEAN.push...{/for}
        //   {#for ITER in CLEAN.orEmpty}...{/for}
        //   {/let}{/let}  (closing RAW and CLEAN)
        String original = content;

        while (true) {
            // Find the init pattern: {#let X=str:split(EXPR)}\n{#let Y=str:split("", "")}
            Pattern initPattern = Pattern.compile(
                    "\\{#let (\\w+)=str:split\\(([^)]+)\\)\\}\\s*" +
                            "\\{#let (\\w+)=str:split\\(\"\", \"\"\\)\\}");
            Matcher m = initPattern.matcher(content);
            if (!m.find())
                break;

            String rawVar = m.group(1);
            String splitExpr = m.group(2);
            String cleanVar = m.group(3);
            int initStart = m.start();
            int afterInit = m.end();

            // Find the push loop: {#for ... in RAW.orEmpty}...CLEAN.push...{/for}
            String afterInitContent = content.substring(afterInit);
            Pattern pushLoopPattern = Pattern.compile(
                    "\\s*\\{#for \\w+ in " + Pattern.quote(rawVar) + "\\.orEmpty\\}");
            Matcher pushLoopMatcher = pushLoopPattern.matcher(afterInitContent);
            if (!pushLoopMatcher.find() || pushLoopMatcher.start() != 0)
                break;

            int pushLoopBodyStart = afterInit + pushLoopMatcher.end();
            int pushLoopEnd = findMatchingEndFor(content, pushLoopBodyStart);
            String pushLoopBody = content.substring(pushLoopBodyStart, pushLoopEnd);
            if (!pushLoopBody.contains(cleanVar + ".push("))
                break;

            int afterPushLoop = pushLoopEnd + "{/for}".length();

            // Find the iteration loop: {#for ITER in CLEAN.orEmpty}
            // May have HTML content between the push loop and the iteration loop
            String afterPushContent = content.substring(afterPushLoop);
            Pattern iterPattern = Pattern.compile(
                    "\\{#for (\\w+) in " + Pattern.quote(cleanVar) + "\\.orEmpty\\}");
            Matcher iterMatcher = iterPattern.matcher(afterPushContent);
            if (!iterMatcher.find())
                break;

            String iterVar = iterMatcher.group(1);
            String contentBetween = afterPushContent.substring(0, iterMatcher.start()).stripLeading();
            int iterBodyStart = afterPushLoop + iterMatcher.end();
            int iterForEnd = findMatchingEndFor(content, iterBodyStart);
            String iterBody = content.substring(iterBodyStart, iterForEnd);

            // Build replacement: preserve HTML between push loop and iteration loop,
            // then a single for loop with str:splitTrimmed
            String replacement = contentBetween +
                    "{#for " + iterVar + " in str:splitTrimmed(" + splitExpr + ").orEmpty}" +
                    iterBody + "{/for}";

            // Remove the {/let}{/let} that closed rawVar and cleanVar.
            // Track {#let}/{/let} depth to skip nested let blocks (e.g. inside
            // an {#if} further down the template).
            String afterFor = content.substring(iterForEnd + "{/for}".length());
            int letDepth = 0;
            int closerStart = -1;
            int closersNeeded = 2;
            Pattern letTag = Pattern.compile("\\{#let\\b|\\{/let\\}");
            Matcher letMatcher = letTag.matcher(afterFor);
            while (letMatcher.find()) {
                if (letMatcher.group().startsWith("{#let")) {
                    letDepth++;
                } else {
                    letDepth--;
                    if (letDepth < 0) {
                        if (closerStart < 0)
                            closerStart = letMatcher.start();
                        closersNeeded--;
                        if (closersNeeded == 0) {
                            afterFor = afterFor.substring(0, closerStart) + afterFor.substring(letMatcher.end());
                            break;
                        }
                    }
                }
            }

            content = content.substring(0, initStart) + replacement + afterFor;
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Collapsed push-in-loop pattern to str:splitTrimmed");
        }
        return content;
    }

    /**
     * Collapses the pattern where a partial iterates over hash entries (source.get(1)),
     * collects nested typed items via push-in-loop, and sorts. This pattern is broken in
     * Qute because {#let} is block-scoped (push results are discarded in loops).
     *
     * Detects:
     * {#let ACCUM=str:split("", ",")}
     * {#for OUTER in SOURCE.orEmpty}
     * {#for ITEM in OUTER.get(1).PATH.get(TYPE_VAR...).orEmpty}
     * {#let ACCUM=ACCUM.push(ITEM)}
     * {/let}{/for}
     * {/for}
     * {#let ACCUM=ACCUM.sort('KEY')}
     *
     * Replaces with:
     * {#let ACCUM=SOURCE.mergeTypes(TYPE_EXPR)}
     */
    private String collapsePushInNestedLoopToMergeTypes(String content) {
        String original = content;

        // Match: {#let ACCUM=str:split("", ",")} or {#let ACCUM=str:split("", "")}
        Pattern initPattern = Pattern.compile(
                "\\{#let (\\w+)=str:split\\(\"\"\\s*,\\s*\"[,\"]\"\\)\\}");
        Matcher initMatcher = initPattern.matcher(content);
        while (initMatcher.find()) {
            String accumVar = initMatcher.group(1);
            int initStart = initMatcher.start();
            int afterInit = initMatcher.end();

            // Look for outer loop: {#for OUTER in SOURCE.orEmpty}
            String afterInitContent = content.substring(afterInit);
            Pattern outerLoopPattern = Pattern.compile(
                    "^\\s*\\{#for (\\w+) in (\\w[\\w.]*)\\.orEmpty\\}");
            Matcher outerMatcher = outerLoopPattern.matcher(afterInitContent);
            if (!outerMatcher.find())
                continue;

            String outerVar = outerMatcher.group(1);
            String source = outerMatcher.group(2);
            int outerBodyStart = afterInit + outerMatcher.end();

            // Look for inner loop: {#for ITEM in OUTER.get(1).SOMETHING.orEmpty}
            // Allow {! ... !} comments (e.g. TODO notes) between outer and inner loops
            String outerBody = content.substring(outerBodyStart);
            Pattern innerLoopPattern = Pattern.compile(
                    "^\\s*(?:\\{!.*?!\\}\\s*)*\\{#for (\\w+) in " + Pattern.quote(outerVar) +
                            "\\.get\\(1\\)\\.\\w+\\.get\\(([^)]+?)(?:\\.or\\(''\\))?\\)\\.orEmpty\\}",
                    Pattern.DOTALL);
            Matcher innerMatcher = innerLoopPattern.matcher(outerBody);
            if (!innerMatcher.find())
                continue;

            String typeExpr = innerMatcher.group(2);

            // Check that the inner loop body contains ACCUM.push
            int innerBodyStart = outerBodyStart + innerMatcher.end();
            int innerForEnd = findMatchingEndFor(content, innerBodyStart);
            String innerBody = content.substring(innerBodyStart, innerForEnd);
            if (!innerBody.contains(accumVar + ".push("))
                continue;

            // Find the outer loop end
            int afterInnerFor = innerForEnd + "{/for}".length();
            String afterInner = content.substring(afterInnerFor);
            Pattern outerEndPattern = Pattern.compile("^\\s*\\{/for\\}");
            Matcher outerEndMatcher = outerEndPattern.matcher(afterInner);
            if (!outerEndMatcher.find())
                continue;
            int outerForEnd = afterInnerFor + outerEndMatcher.end();

            // Look for sort: {#let ACCUM=ACCUM.sort('KEY')}
            String afterOuterLoop = content.substring(outerForEnd);
            Pattern sortPattern = Pattern.compile(
                    "^\\s*\\{#let " + Pattern.quote(accumVar) + "=" +
                            Pattern.quote(accumVar) + "\\.sort\\('[^']*'\\)\\}");
            Matcher sortMatcher = sortPattern.matcher(afterOuterLoop);
            int replaceEnd;
            if (sortMatcher.find()) {
                replaceEnd = outerForEnd + sortMatcher.end();
            } else {
                replaceEnd = outerForEnd;
            }

            // Remove the extra {/let} closings for the init and sort scopes
            String afterReplace = content.substring(replaceEnd);
            int letsToRemove = sortMatcher.find(0) ? 2 : 1;
            for (int i = 0; i < letsToRemove; i++) {
                afterReplace = afterReplace.replaceFirst("\\{/let\\}", "");
            }

            // Build replacement
            String replacement = "{#let " + accumVar + "=" + source + ".mergeTypes(" + typeExpr + ")}";
            content = content.substring(0, initStart) + replacement + afterReplace;

            // Reset matcher since content changed
            initMatcher = initPattern.matcher(content);
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Collapsed push-in-nested-loop to mergeTypes()");
        }
        return content;
    }

    private int findScopeBoundary(String content, int startPos) {
        // Place {/let} BEFORE the next enclosing block's closing tag at depth 0.
        // This ensures valid nesting: the let closes before its enclosing block closes.
        // {#else} is also a boundary — assigns in if-branches scope to before the else.
        // (If/else assigns for the same variable are handled separately by convertIfElseAssignsToTernary.)
        int depth = 0;
        Pattern tagPattern = Pattern.compile("\\{#(for|if|let)\\b|\\{#else\\b|\\{/(for|if|let)\\}");
        Matcher matcher = tagPattern.matcher(content);
        matcher.region(startPos, content.length());

        while (matcher.find()) {
            String match = matcher.group();
            if (match.startsWith("{#else")) {
                if (depth == 0) {
                    return matcher.start();
                }
            } else if (match.startsWith("{#")) {
                depth++;
            } else {
                if (depth == 0) {
                    return matcher.start();
                }
                depth--;
            }
        }

        return content.length();
    }

    private int findEnclosingBlockEnd(String content, int startPos) {
        // Find the closing tag of the nearest enclosing block (skips past {#else}).
        // Unlike findScopeBoundary, this goes UP one nesting level to find where
        // the entire if/else block ends.
        int depth = 0;
        Pattern tagPattern = Pattern.compile("\\{#(for|if|let)\\b|\\{/(for|if|let)\\}");
        Matcher matcher = tagPattern.matcher(content);
        matcher.region(startPos, content.length());
        while (matcher.find()) {
            if (matcher.group().startsWith("{#")) {
                depth++;
            } else {
                depth--;
                if (depth < 0) {
                    return matcher.end();
                }
            }
        }
        return content.length();
    }

    private String convertCaseStatements(String content) {
        Pattern caseBlockPattern = Pattern.compile(
                "\\{%\\s*case\\s+([^%]+?)\\s*%\\}(.*?)\\{%\\s*endcase\\s*%\\}", Pattern.DOTALL);
        Matcher matcher = caseBlockPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;

        while (matcher.find()) {
            changed = true;
            String var = matcher.group(1).trim();
            String body = matcher.group(2);

            boolean[] first = { true };
            Pattern whenPattern = Pattern.compile("\\{%\\s*when\\s+([^%]+?)\\s*%\\}");
            Matcher whenMatcher = whenPattern.matcher(body);
            StringBuilder bodySb = new StringBuilder();
            while (whenMatcher.find()) {
                String val = whenMatcher.group(1).trim();
                String tag = first[0] ? "{#if " + var + " == " + val + "}"
                        : "{#else if " + var + " == " + val + "}";
                whenMatcher.appendReplacement(bodySb, Matcher.quoteReplacement(tag));
                first[0] = false;
            }
            whenMatcher.appendTail(bodySb);

            matcher.appendReplacement(sb, Matcher.quoteReplacement(bodySb.toString() + "{/if}"));
        }
        matcher.appendTail(sb);

        if (changed) {
            conversionsApplied.add("Converted case statements to if/else if chains");
        }
        return sb.toString();
    }

    private String convertLayoutTags(String content) {
        String original = content;

        // Jekyll layout declaration — in Roq, layout is set via front matter, not a template tag
        content = content.replaceAll("\\{%\\s*layout\\s+[\"']([^\"']+)[\"']\\s*%\\}",
                "{! TODO: set layout in front matter instead: layout: $1 !}");

        // {% block name %} -> {#block name}
        content = content.replaceAll("\\{%\\s*block\\s+(\\w+)\\s*%\\}", "{#block $1}");
        content = content.replaceAll("\\{%\\s*endblock\\s*%\\}", "{/block}");

        // {% append name %} -> {#append name}
        content = content.replaceAll("\\{%\\s*append\\s+(\\w+)\\s*%\\}", "{#append $1}");
        content = content.replaceAll("\\{%\\s*endappend\\s*%\\}", "{/append}");

        // {% prepend name %} -> {#prepend name}
        content = content.replaceAll("\\{%\\s*prepend\\s+(\\w+)\\s*%\\}", "{#prepend $1}");
        content = content.replaceAll("\\{%\\s*endprepend\\s*%\\}", "{/prepend}");

        // Jekyll's {{ content }} renders child content.
        // In layouts: Qute uses {#insert /} for standalone output.
        //   When content is used as a variable (e.g. content | split), use site.pageContent(page)
        //   which renders the page's inner content as a string.
        // In partials: use {page.content} since {#insert /} causes infinite recursion
        if (convertingPartials) {
            content = content.replaceAll("\\{=content\\}", exprOpen + "page.content}");
        } else {
            content = content.replaceAll("\\{=content\\}", "{#insert /}");
            // Replace remaining content variable references (used as function args, etc.)
            // with site.pageContent(page) — matches content when used as a variable in Qute
            // expressions (after ( or , or =) but not as plain text
            content = content.replaceAll("(?<=[=(,])content(?=[,)\\s}])", "site.pageContent(page)");
            // Page content is pre-rendered HTML — use splitRaw to prevent Qute auto-escaping
            content = content.replace("str:split(site.pageContent(page)", "str:splitRaw(site.pageContent(page)");
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Converted layout tags");
        }
        return content;
    }

    private String convertSpecialTags(String content) {
        // Highlight blocks (for code syntax highlighting)
        // Liquid: {% highlight lang %}...{% endhighlight %}
        // Qute: <pre><code class="language-lang">...</code></pre>
        Pattern highlightPattern = Pattern.compile("\\{%\\s*highlight\\s+(\\w+)\\s*%\\}(.*?)\\{%\\s*endhighlight\\s*%\\}",
                Pattern.DOTALL);
        Matcher highlightMatcher = highlightPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (highlightMatcher.find()) {
            String lang = highlightMatcher.group(1);
            String code = highlightMatcher.group(2);
            String replacement = "<pre><code class=\"language-" + lang + "\">" + code + "</code></pre>";
            highlightMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        highlightMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted special tags");
        }

        return result;
    }

    private String convertBracketNotation(String content) {
        // Liquid: object[variable] (dynamic property access)
        // Qute: object.get(variable)
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_.]+)\\[([a-zA-Z0-9_.]+)\\]");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String object = matcher.group(1);
            String key = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(object + ".get(" + key + ")"));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted bracket notation to .get()");
        }

        return result;
    }

    private String wrapTernaryBeforeMethods(String content) {
        return transformInsideExpressions(content, this::wrapTernaryInExpression,
                "Wrapped ternary operators before method calls");
    }

    private String wrapTernaryInExpression(String expr) {
        // Match ?: where the default value is followed by a method/property call:
        //   expr ?: "val".method  →  expr.or("val").method
        // Qute doesn't support method chaining on parenthesized ternary expressions,
        // so we use .or() which returns the value or the fallback and supports chaining.
        Pattern pattern = Pattern.compile(
                "([a-zA-Z0-9_\\.\\[\\]]+)\\s*\\?:\\s*([\"'][^\"']*[\"']|[a-zA-Z0-9_\\.\\[\\]]+)\\.([a-zA-Z0-9_]+)(\\s*\\()?");
        Matcher matcher = pattern.matcher(expr);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variable = matcher.group(1);
            String defaultVal = matcher.group(2);
            String method = matcher.group(3);
            String paren = matcher.group(4) != null ? matcher.group(4) : "";
            String replacement = variable + ".or(" + defaultVal + ")." + method + paren;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String removeSpacesBeforeMethods(String content) {
        return transformInsideExpressions(content, this::removeSpacesInExpression,
                "Removed spaces before method calls");
    }

    private String removeSpacesInExpression(String expr) {
        // Match: identifier/paren/bracket/quote + whitespace + dot + identifier
        // This handles cases like "stripHtml .wordLimit" → "stripHtml.wordLimit"
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\)\\]\"'])\\s+\\.([a-zA-Z0-9_]+)");
        String result = expr;
        String prev;

        // Keep applying until no more matches (handles multiple spaces in a row)
        do {
            prev = result;
            result = pattern.matcher(result).replaceAll("$1.$2");
        } while (!result.equals(prev));

        return result;
    }

    private String transformInsideExpressions(String content,
            java.util.function.UnaryOperator<String> transform, String conversionLabel) {
        Pattern exprPattern = useExtensionSyntax
                ? Pattern.compile("\\{[=#/!][^}]*\\}")
                : Pattern.compile("\\{[^}]*\\}");
        Matcher matcher = exprPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        boolean changed = false;
        while (matcher.find()) {
            String expr = matcher.group();
            String transformed = transform.apply(expr);
            if (!transformed.equals(expr)) {
                changed = true;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(transformed));
        }
        matcher.appendTail(sb);

        if (changed) {
            conversionsApplied.add(conversionLabel);
        }

        return sb.toString();
    }

    private String convertSiteDataReferences(String content) {
        String original = content;
        Pattern p = Pattern.compile("\\bsite\\.data\\.(\\w+)");
        Matcher m = p.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            if (name.equals("get")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, "cdi:" + name);
            }
        }
        m.appendTail(sb);
        content = sb.toString();
        if (!content.equals(original)) {
            conversionsApplied.add("Converted site.data references to cdi: prefix");
        }
        return content;
    }

    private String convertSiteDataProperties(String content) {
        // Jekyll site.* properties that aren't part of Roq's Site model come from _config.yml.
        // These are migrated to data/siteConfig.yml and accessed via cdi:siteConfig.*
        // Note: Using "siteConfig" instead of "site" to avoid conflict with Roq's built-in Site object
        String knownSiteProps = "url|title|description|image|imageExists|data|pages|allPages|collections|" +
                "index|files|file|fileExists|page|normalPage|document|imagesDirUrl|pageContent|" +
                "posts|tags|time";

        // Match site.prop and optionally .sub-prop chains (YAML keys may contain hyphens)
        Pattern pattern = Pattern.compile(
                "\\bsite\\.((?!(?:" + knownSiteProps
                        + ")\\b)[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*(?:-[a-zA-Z][a-zA-Z0-9_]*)*)*)");
        StringBuffer sb = new StringBuffer();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            String propChain = m.group(1);
            // CamelCase any hyphenated segments so Qute dot notation works
            String camelCased = camelCasePropertyChain(propChain);
            m.appendReplacement(sb, "cdi:siteConfig." + camelCased);
        }
        m.appendTail(sb);
        String result = sb.toString();

        if (!result.equals(content)) {
            conversionsApplied.add("Converted site data properties to CDI references");
        }

        return result;
    }

    private static String camelCasePropertyChain(String chain) {
        String[] segments = chain.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0)
                sb.append('.');
            sb.append(JekyllConfigConverter.hyphenToCamelCase(segments[i]));
        }
        return sb.toString();
    }

    private String convertAutopagesVariables(String content) {
        String original = content;
        // Jekyll paginate-v2 autopages injects page.pagination.{entity}_data (the entity's
        // data hash) and page.pagination.{entity} (the entity key) for each autopage type
        // (author, tag, category, collection). After convertCustomPageFields these become
        // page.data.pagination.{entity}_data and page.data.pagination.{entity}.
        // In Roq, from-data pages put the data fields directly on page.data, and the
        // YAML map key is available as page.data._key (when id-key=_key).
        // Replace _data variant first (longer match) to avoid partial substitution.
        content = content.replaceAll("page\\.data\\.pagination\\.(\\w+)_data", "page.data");
        content = content.replaceAll("page\\.data\\.pagination\\.(\\w+)", "page.data._key");
        if (!content.equals(original)) {
            conversionsApplied.add("Converted Jekyll autopages variables to Roq from-data");
        }
        return content;
    }

    private String convertCustomPageFields(String content) {
        // Map Jekyll built-in properties to their Roq equivalents
        String result = content.replaceAll("\\b(page|post)\\.path\\b", "$1.sourcePath");
        if (!result.equals(content)) {
            conversionsApplied.add("Mapped page.path to page.sourcePath");
            content = result;
        }

        // Roq's Page model has specific built-in properties. Custom frontmatter must use page.data.*
        // This applies to 'page' and page-like loop variables like 'post'
        String knownPageProps = "url|title|description|image|imageExists|date|data|content|contentAbstract|" +
                "rawTemplate|sourcePath|sourceFileName|baseFileName|id|draft|files|file|fileExists|source|site|" +
                "collectionId|collection|next|nextPage|previous|prev|previousPage|prevPage|hidden|paginator|" +
                "tags|tagsCount";

        // Match page.customField or post.customField (not *.data.*, *.url.*, or known properties)
        // and convert to *.data.customField
        Pattern pattern = Pattern.compile(
                "(?<!-)\\b(page|post)\\.((?!(?:" + knownPageProps + ")\\b|data\\.|url\\.)[a-zA-Z_][a-zA-Z0-9_]*)\\b");
        result = pattern.matcher(content).replaceAll("$1.data.$2");

        if (!result.equals(content)) {
            conversionsApplied.add("Converted custom page frontmatter fields to page.data.*");
        }

        return result;
    }

    private String makePageDataLenient(String content) {
        // page.data.* and post.data.* access a JsonObject.
        // Do NOT add ?? — Qute passes "property??" as the literal key name to
        // JsonObject.getValue(), which fails because the key is "property".
        //
        // BUT: when a .data.* property is used as an argument to str:split/str:splitTrimmed,
        // a missing key returns Results$NotFound which can't be cast to String → ClassCastException.
        // Add .or('') to those arguments so the split receives an empty string instead.
        String result = content.replaceAll(
                "(str:split(?:Trimmed)?\\()((page|post)\\.data\\.[a-zA-Z_][a-zA-Z0-9_]*)(,)",
                "$1$2.or('')$4");
        if (!result.equals(content)) {
            conversionsApplied.add("Added .or('') to .data.* properties in split arguments");
            content = result;
        }

        // Same for .get() arguments — JsonObjectValueResolver.get(param) casts param
        // to String, so Results$NotFound → ClassCastException.
        // TODO: Remove .or('') workaround when Quarkus 3.38+ is the minimum version —
        //       JsonObjectValueResolver will handle NotFound gracefully in 3.38.
        result = content.replaceAll(
                "(\\.get\\()((page|post)\\.data\\.[a-zA-Z_][a-zA-Z0-9_]*)(\\))",
                "$1$2.or('')$4");
        if (!result.equals(content)) {
            conversionsApplied.add("Added .or('') to .data.* properties in .get() arguments");
            content = result;
        }

        // Also protect plain variable arguments in .get() calls — a variable from a
        // prior {#let} may hold Results$NotFound if the source expression was missing.
        // Skip string/number literals and .data.* args (already handled above).
        // TODO: Remove .or('') workaround when Quarkus 3.38+ is the minimum version.
        result = addOrEmptyToGetArgs(content);
        if (!result.equals(content)) {
            conversionsApplied.add("Added .or('') to variable arguments in .get() calls");
            content = result;
        }

        // Guard {=page.data.FIELD} output — Liquid outputs empty string for missing
        // keys, but Qute renders NOT_FOUND.  Add .or('') so the output is blank.
        // (.raw is appended later by appendRawToOutputExpressions)
        result = content.replaceAll(
                "\\{=((page|post)\\.data\\.[a-zA-Z_][a-zA-Z0-9_]*)\\}",
                "{=$1.or('')}");
        if (!result.equals(content)) {
            conversionsApplied.add("Added .or('') to .data.*.raw output expressions");
            content = result;
        }

        // Guard {=*.data.* | tocify_asciidoc} — the filter crashes when the property
        // is missing (e.g. non-AsciiDoc pages using a layout that expects AsciiDoc)
        result = content.replaceAll(
                "\\{=((?:page|post)\\.data\\.[a-zA-Z_][a-zA-Z0-9_]*) \\| tocify_asciidoc\\}",
                "{#if $1}{=$1 | tocify_asciidoc}{/if}");
        if (!result.equals(content)) {
            conversionsApplied.add("Guarded tocify_asciidoc filter on .data.* properties");
        }

        return result;
    }

    private String addOrEmptyToGetArgs(String content) {
        String[] lines = content.split("\n", -1);
        Pattern pattern = Pattern.compile("\\.get\\(([a-zA-Z_][a-zA-Z0-9_]*)\\)");
        boolean changed = false;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                String cleanVersion = line.trim();
                line = pattern.matcher(line).replaceAll(".get($1.or(''))");
                String indent = line.substring(0, line.indexOf(line.trim()));
                result.append(indent)
                        .append("{! TODO: Quarkus 3.38 fixes NotFound in .get() — remove .or('') to get: ")
                        .append(cleanVersion).append(" !}\n");
                changed = true;
            }
            result.append(line);
            if (i < lines.length - 1) {
                result.append('\n');
            }
        }

        return changed ? result.toString() : content;
    }

    private String convertUrlConcatenation(String content) {
        // RoqUrl needs .resolve() for URL concatenation, not .concat().
        // Patterns:
        // - site.url.concat(page.url) -> site.url.resolve(page.url)
        // - page.url.concat("/path") -> page.url.resolve("/path")
        Pattern pattern = Pattern.compile(
                "((?:site|page)\\.url)\\.concat\\(([^)]+?)\\)");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String urlExpr = matcher.group(1); // site.url or page.url
            String arg = matcher.group(2).trim();

            String replacement = urlExpr + ".resolve(" + arg + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted URL concatenation to .resolve()");
        }

        return result;
    }

    private String convertStandaloneSiteUrl(String content) {
        String original = content;
        // Match site.url NOT followed by a dot (method call / property access)
        content = content.replaceAll("\\bsite\\.url\\b(?!\\.)", "site.url.root.url");
        if (!content.equals(original)) {
            conversionsApplied.add("Converted standalone site.url to site.url.root.url (Jekyll site.url is a base URL string)");
        }
        return content;
    }

    private String convertPageUrlComparisons(String content) {
        // RoqUrl is an object, not a String. Equality comparisons like page.url == '/'
        // need to use page.url.path instead so they compare strings.
        Pattern pattern = Pattern.compile("\\bpage\\.url\\s*(==|!=|\\bne\\b)");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement("page.url.path " + matcher.group(1)));
            found = true;
        }
        matcher.appendTail(sb);

        if (found) {
            conversionsApplied.add("Converted page.url comparisons to page.url.path (RoqUrl is not a String)");
            content = sb.toString();
        }

        // RoqUrl.replaceAll() and .replace() return RoqUrl, not String.
        // When the result is used as a String (e.g. as a JsonObject key),
        // this causes a ClassCastException. Use page.url.path instead.
        String original = content;
        content = content.replaceAll("\\bpage\\.url\\.replaceAll\\(", "page.url.path.replaceAll(");
        content = content.replaceAll("\\bpage\\.url\\.replace\\(", "page.url.path.replace(");
        if (!content.equals(original)) {
            conversionsApplied.add(
                    "Converted page.url.replaceAll/replace to page.url.path.replaceAll/replace (RoqUrl methods return RoqUrl, not String)");
        }

        return content;
    }

    private String convertSiteCollections(String content) {
        String original = content;
        // Jekyll's site.posts → Roq's site.collections.get('posts')
        content = content.replaceAll("\\bsite\\.posts\\b", "site.collections.get('posts')");
        if (!content.equals(original)) {
            conversionsApplied.add("Converted site.posts to site.collections.get('posts')");
        }

        // Jekyll's site.time is the build time (not config). Convert to Roq's now global.
        // The rfc822 extension in JekyllFiltersExtension handles LocalDateTime (assumes UTC).
        original = content;
        content = content.replaceAll("\\bsite\\.time\\.rfc822\\b", "now.rfc822");
        content = content.replaceAll("\\bsite\\.time\\b", "now");
        if (!content.equals(original)) {
            conversionsApplied.add("Converted site.time to now (Roq build-time global)");
        }

        return content;
    }

    private String appendRawToOutputExpressions(String content) {
        // Liquid {{ var }} never HTML-escapes, but Qute {=var} auto-escapes in .html templates.
        // Many Jekyll data files (YAML) contain embedded HTML (links, formatting, etc.) that
        // must render as markup, not as escaped text. Since the converter can't know which
        // fields contain HTML, we append .raw to ALL output expressions for Liquid fidelity.
        Pattern pattern = useExtensionSyntax
                ? Pattern.compile("\\{=([^}]+)\\}")
                : Pattern.compile("\\{(?![#/!|%])([^}]+)\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (matcher.find()) {
            String expr = matcher.group(1);
            // Skip if already ends with .raw (possibly followed by ??)
            if (expr.matches(".*\\.raw(\\?\\?)?\\s*$")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            // Skip pure string literals (but not string.concat(...) chains)
            if (expr.trim().matches("^['\"][^'\"]*['\"]\\s*$")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            // Skip elvis operator expressions — .raw can't wrap the whole ternary result
            if (expr.contains("?:")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            // Insert .raw before ?? if present, otherwise append at end
            if (expr.endsWith("??")) {
                String base = expr.substring(0, expr.length() - 2);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(exprOpen + base + ".raw??" + "}"));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(exprOpen + expr + ".raw}"));
            }
            found = true;
        }
        matcher.appendTail(sb);

        if (found) {
            conversionsApplied.add("Appended .raw to output expressions (Liquid never escapes HTML)");
            return sb.toString();
        }
        return content;
    }

    private String makeSiteTagsLenient(String content) {
        // Jekyll's site.tags is auto-generated and doesn't exist in Roq by default.
        // Replace with cdi:siteConfig.tags which is set to an empty list by the migration script.

        Pattern pattern = Pattern.compile("\\bsite\\.tags\\b");
        Matcher matcher = pattern.matcher(content);

        if (!matcher.find()) {
            return content; // No site.tags usage
        }

        // Split frontmatter from template body
        String frontmatter = "";
        String body = content;

        Pattern frontmatterPattern = Pattern.compile("^(---\\s*\\n.*?\\n---\\s*\\n)", Pattern.DOTALL);
        Matcher fmMatcher = frontmatterPattern.matcher(content);
        if (fmMatcher.find()) {
            frontmatter = fmMatcher.group(1);
            body = content.substring(fmMatcher.end());
        }

        // Add a warning comment at the start of the body
        String warning = "\n{! TODO: site.tags not available in Roq by default.\n" +
                "   The tag listing feature is currently disabled (replaced with cdi:siteConfig.tags=[]).\n" +
                "   Options to restore functionality:\n" +
                "   (1) Use collection.tagsCount() for a specific collection,\n" +
                "   (2) Add a site.tags extension method, or\n" +
                "   (3) Remove the tag listing feature entirely.\n" +
                "   See migration implementation notes for details. !}\n";

        // Replace all site.tags with cdi:siteConfig.tags (which is an empty list)
        body = pattern.matcher(body).replaceAll("cdi:siteConfig.tags");

        String result = frontmatter + warning + body;

        conversionsApplied.add("Replaced site.tags with cdi:siteConfig.tags (needs manual implementation)");

        return result;
    }
}
