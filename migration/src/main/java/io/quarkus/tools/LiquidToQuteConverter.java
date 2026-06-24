package io.quarkus.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiquidToQuteConverter {

    private final boolean useExtensionSyntax;
    private final String exprOpen;
    private final List<String> conversionsApplied = new ArrayList<>();
    private boolean convertingPartials;

    LiquidToQuteConverter() {
        this(true);
    }

    LiquidToQuteConverter(boolean useExtensionSyntax) {
        this.useExtensionSyntax = useExtensionSyntax;
        this.exprOpen = useExtensionSyntax ? "{=" : "{";
    }

    void setConvertingPartials(boolean convertingPartials) {
        this.convertingPartials = convertingPartials;
    }

    String convert(String content) {
        String original = content;

        // Strip Liquid whitespace-trimming markers before any conversion
        content = content.replaceAll("\\{%-", "{%");
        content = content.replaceAll("-%\\}", "%}");

        // Extract raw blocks before any conversion to preserve their content verbatim
        List<String> rawBlocks = new ArrayList<>();
        content = extractRawBlocks(content, rawBlocks);

        // Convert in order of complexity
        content = convertComments(content);
        content = convertVariables(content);
        content = convertFilters(content);
        content = convertLoops(content);
        content = convertConditionals(content);
        content = convertPaginator(content);
        content = convertIncludes(content);
        content = convertIncludeParamAccess(content);

        // Convert if/else blocks with assigns to ternary expressions (must run before convertAssignments)
        content = convertIfElseAssignsToTernary(content);

        content = convertAssignments(content);

        // Collapse "init empty list + push in loop + iterate" into str:splitTrimmed
        // Must run after convertAssignments (which creates {#let}) and convertLoops
        content = collapsePushInLoopPattern(content);

        content = convertCaseStatements(content);
        content = convertLayoutTags(content);
        content = convertSpecialTags(content);

        content = convertBracketNotation(content);

        // Final cleanup steps - ORDER MATTERS!
        // Remove spaces first so ternary wrapping can match properly
        content = removeSpacesBeforeMethods(content);
        content = wrapTernaryBeforeMethods(content);

        // Convert site.data.X references to cdi:X (Roq data file access)
        content = convertSiteDataReferences(content);

        // Convert site properties that come from data/site.yml to CDI references
        content = convertSiteDataProperties(content);

        // Convert custom page frontmatter fields to page.data.*
        content = convertCustomPageFields(content);

        // Make page.data.* references lenient — custom frontmatter may not exist on every page
        content = makePageDataLenient(content);

        // Convert URL concatenation to RoqUrl methods
        content = convertUrlConcatenation(content);

        // Convert page.url equality comparisons to use .path (RoqUrl is not a String)
        content = convertPageUrlComparisons(content);

        // Convert Jekyll site.posts to Roq collections access
        content = convertSiteCollections(content);

        // Make site.tags lenient (not available in Roq by default)
        content = makeSiteTagsLenient(content);

        // Append .raw to output expressions containing .replace() calls.
        // Jekyll never escapes output, but Qute auto-escapes in HTML templates.
        content = appendRawToReplaceOutputs(content);

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
                ? Pattern.compile("\\{=[^}]*\\}|\\{%[^%]*%\\}")
                : Pattern.compile("\\{(?![%#/!|])[^}]*\\}|\\{%[^%]*%\\}");
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
        // Jekyll's | relative_url prepends site.baseurl to a path.
        // Rewrite as | prepend: so the concatenation filter handles the expression walk.
        content = content.replaceAll("\\|\\s*relative_url", "| prepend: cdi:siteConfig.baseurl");
        // TODO: absolute_url should prepend site.url + site.baseurl, but chaining two prepends
        // interacts badly with convertUrlConcatenation's site.url.resolve() transform.
        // For now, treat same as relative_url (sufficient when site.url is not needed).
        content = content.replaceAll("\\|\\s*absolute_url", "| prepend: cdi:siteConfig.baseurl");
        return content;
    }

    private String convertConcatenationFilters(String content) {
        // Append filter: "text" | append: variable | append: "more" -> "text" + variable + "more"
        Pattern appendPattern = Pattern.compile("([^|{]+?)((?:\\s*\\|\\s*append:\\s*[^|]+)+)");
        Matcher appendMatcher = appendPattern.matcher(content);
        StringBuilder appendSb = new StringBuilder();

        while (appendMatcher.find()) {
            String base = appendMatcher.group(1).trim();
            String appends = appendMatcher.group(2);

            Pattern appendValuePattern = Pattern.compile("\\|\\s*append:\\s*([^|]+?)(?=\\s*\\||$)");
            Matcher appendValueMatcher = appendValuePattern.matcher(appends);
            StringBuilder concatenation = new StringBuilder(base);

            while (appendValueMatcher.find()) {
                concatenation.append(" + ").append(appendValueMatcher.group(1).trim());
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
            conversionsApplied.add("Converted append filter to string concatenation");
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
                } else if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                    baseStart--;
                } else {
                    break;
                }
            }

            if (baseStart >= baseEnd) {
                break;
            }

            String base = content.substring(baseStart, baseEnd);
            content = content.substring(0, baseStart) + value + " + " + base + content.substring(m.end());
            conversionsApplied.add("Converted prepend filter to string concatenation");
        }

        return content;
    }

    private String convertTableDrivenFilters(String content) {
        // Filters with a single argument: | filter: arg -> .method(arg)
        String[][] filterWithArgMap = {
                { "sort", "sort" },
                { "startswith", "startsWith" },
                { "endswith", "endsWith" },
                { "contains", "contains" },
                { "equals", "equals" },
                { "map", "map" },
                { "group_by", "groupBy" },
                { "slice", "slice" },
                { "add", "add" },
                { "minus", "minus" },
                { "times", "times" },
                { "truncate", "truncate" },
                { "remove_first", "removeFirst" },
                { "where_exp", "whereExp" },
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
                { "escape", "escapeHtml" },
                { "url_encode", "urlEncode" },
                { "slugify", "slugify" }
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

            dateMatcher.appendReplacement(sb, ".format('" + javaFormat + "')");
        }
        dateMatcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted date filters");
            content = result;
        }

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

    private String convertSplitFilter(String content) {
        // Use namespace form str:split(base, delim) instead of base.split(delim).
        // Namespace extensions receive null as a regular parameter, so they handle
        // null base objects that instance extensions can't dispatch on.
        Pattern splitPattern = Pattern.compile("([a-zA-Z0-9_\\.\"'\\[\\]()]+)\\s*\\|\\s*split:\\s*(['\"][^'\"]*['\"])");
        Matcher m = splitPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (m.find()) {
            String base = m.group(1);
            String delim = m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement("str:split(" + base + ", " + delim + ")"));
            found = true;
        }
        m.appendTail(sb);
        if (found) {
            conversionsApplied.add("Converted split filter");
            return sb.toString();
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

    private String convertPaginator(String content) {
        String original = content;

        // Jekyll's paginator.posts -> Roq's collection access
        content = content.replaceAll("\\bpaginator\\.posts\\b",
                "site.collections.get('posts').paginated(page.paginator)");

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

        if (!content.equals(original)) {
            conversionsApplied.add("Converted Jekyll paginator to Roq pagination");
        }
        return content;
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

        // Handle limit and offset
        Pattern limitPattern = Pattern
                .compile("\\{#for\\s+(\\w+)\\s+in\\s+([^}]+?)\\s+limit:(\\d+)(?:\\s+offset:(\\d+))?\\s*\\}");
        Matcher limitMatcher = limitPattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (limitMatcher.find()) {
            String replacement = buildLimitedForLoop(limitMatcher);
            limitMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        limitMatcher.appendTail(sb);
        content = sb.toString();

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

    private static String buildLimitedForLoop(Matcher limitMatcher) {
        String var = limitMatcher.group(1);
        String collection = limitMatcher.group(2);
        String limit = limitMatcher.group(3);
        String offset = limitMatcher.group(4);

        if (offset != null && !offset.equals("0")) {
            return "{#for " + var + " in " + collection + ".skip(" + offset + ").limit(" + limit + ")}";
        } else {
            return "{#for " + var + " in " + collection + ".limit(" + limit + ")}";
        }
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
                replacement = "{#include " + path + " " + params + " /}";
            } else {
                replacement = "{#include " + path + " /}";
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

    private String convertIfElseAssignsToTernary(String content) {
        // Detect if/else blocks where the same variable is assigned in both branches.
        // Replace both assigns with a single ternary assign BEFORE the if block.
        // The result is still a {% assign %} tag, so convertAssignments handles scoping.

        Pattern ifElsePattern = Pattern.compile(
                "\\{#if\\s+([^}]+?)\\}(.*?)\\{#else\\}(.*?)\\{/if\\}",
                Pattern.DOTALL);

        Matcher matcher = ifElsePattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;

        while (matcher.find()) {
            String condition = matcher.group(1);
            String ifBranch = matcher.group(2);
            String elseBranch = matcher.group(3);

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

                    // Qute doesn't support ternary (? :), so use alternative constructs.
                    // 'contains' is only valid as an operator in {#if} sections —
                    // convert to method call for use in {#let} expressions.
                    String exprCondition = condition.replaceAll(
                            "(\\S+)\\s+contains\\s+('[^']*'|\"[^\"]*\")",
                            "$1.contains($2)");

                    String combinedExpr;
                    if (condition.trim().equals(ifExpr.trim())) {
                        // condition ? condition : fallback  →  condition ?: fallback
                        combinedExpr = ifExpr + " ?: " + elseExpr;
                    } else {
                        // General case: find trailing content that uses this variable
                        // and duplicate it into each branch so {#let} scope covers it
                        int afterIfElse = matcher.end();
                        String trailing = content.substring(afterIfElse);
                        int useEnd = findTrailingUsageEnd(trailing, var);
                        if (useEnd > 0) {
                            String trailingContent = trailing.substring(0, useEnd);
                            String ifAssign = "{% assign " + var + " = " + ifExpr + " %}";
                            String elseAssign = "{% assign " + var + " = " + elseExpr + " %}";
                            String before = content.substring(0, matcher.start());
                            String after = trailing.substring(useEnd);
                            String result = before
                                    + "{#if " + condition + "}"
                                    + ifAssign + trailingContent
                                    + "{#else}"
                                    + elseAssign + trailingContent
                                    + "{/if}" + after;
                            conversionsApplied.add("Inlined trailing content into if/else branches for variable scope");
                            return result;
                        }
                        continue;
                    }

                    ternaryAssigns.append("{% assign ").append(var).append(" = ")
                            .append(combinedExpr).append(" %}\n");

                    modifiedIfBranch = modifiedIfBranch.replaceFirst(
                            "\\{%\\s*assign\\s+" + var + "\\s*=\\s*" + Pattern.quote(ifExpr) + "\\s*%\\}",
                            "");
                    modifiedElseBranch = modifiedElseBranch.replaceFirst(
                            "\\{%\\s*assign\\s+" + var + "\\s*=\\s*" + Pattern.quote(elseExpr) + "\\s*%\\}",
                            "");
                }

                if (ternaryAssigns.length() == 0) {
                    // No variables could be converted, leave unchanged
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                    continue;
                }

                // Check if the if/else block is now empty (only whitespace)
                boolean ifEmpty = modifiedIfBranch.trim().isEmpty();
                boolean elseEmpty = modifiedElseBranch.trim().isEmpty();

                String replacement;
                if (ifEmpty && elseEmpty) {
                    // Both branches are empty after removing assigns — drop the if/else entirely
                    replacement = ternaryAssigns.toString();
                } else {
                    replacement = ternaryAssigns.toString()
                            + "{#if " + condition + "}"
                            + modifiedIfBranch
                            + "{#else}"
                            + modifiedElseBranch
                            + "{/if}";
                }

                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                changed = true;
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        if (changed) {
            conversionsApplied.add("Converted if/else assigns to ternary expressions");
            return sb.toString();
        }

        return content;
    }

    private int findTrailingUsageEnd(String trailing, String varName) {
        // Find the end of the first line that uses the variable.
        // Only inline a small amount of trailing content to avoid duplicating
        // large template blocks.
        Pattern usePattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
        Matcher useMatcher = usePattern.matcher(trailing);
        if (!useMatcher.find()) {
            return 0;
        }
        // Only inline if the first usage is within the first few lines
        int firstUse = useMatcher.start();
        long newlinesBefore = trailing.substring(0, firstUse).chars().filter(c -> c == '\n').count();
        if (newlinesBefore > 3) {
            return 0;
        }
        // Extend to the end of the line containing the last usage on consecutive lines
        int lineEnd = trailing.indexOf('\n', useMatcher.end());
        return lineEnd >= 0 ? lineEnd + 1 : trailing.length();
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
            content = content.substring(0, assignStart)
                    + "{#let " + var + "=" + expr + "}"
                    + content.substring(assignEnd, scopeEnd)
                    + "{/let}"
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

            // Remove the trailing {/let}{/let} that closed rawVar and cleanVar
            String afterFor = content.substring(iterForEnd + "{/for}".length());
            afterFor = afterFor.replaceFirst("\\{/let\\}\\{/let\\}", "");

            content = content.substring(0, initStart) + replacement + afterFor;
        }

        if (!content.equals(original)) {
            conversionsApplied.add("Collapsed push-in-loop pattern to str:splitTrimmed");
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
        // In layouts: Qute uses {#insert /}
        // In partials: use {page.content} since {#insert /} causes infinite recursion
        if (convertingPartials) {
            content = content.replaceAll("\\{=content\\}", exprOpen + "page.content}");
        } else {
            content = content.replaceAll("\\{=content\\}", "{#insert /}");
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
        Pattern pattern = Pattern.compile(
                "([a-zA-Z0-9_\\.\\[\\]]+)\\s*\\?:\\s*([\"'][^\"']*[\"']|[a-zA-Z0-9_\\.\\[\\]]+)\\.([a-zA-Z0-9_]+)\\s*\\(");
        Matcher matcher = pattern.matcher(expr);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variable = matcher.group(1);
            String defaultVal = matcher.group(2);
            String method = matcher.group(3);
            String replacement = "(" + variable + " ?: " + defaultVal + ")." + method + "(";
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
                "posts|tags";

        Pattern pattern = Pattern.compile(
                "\\bsite\\.((?!(?:" + knownSiteProps + ")\\b)[a-zA-Z_][a-zA-Z0-9_]*)\\b");
        String result = pattern.matcher(content).replaceAll("cdi:siteConfig.$1");

        if (!result.equals(content)) {
            conversionsApplied.add("Converted site data properties to CDI references");
        }

        return result;
    }

    private String convertCustomPageFields(String content) {
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
        String result = pattern.matcher(content).replaceAll("$1.data.$2");

        if (!result.equals(content)) {
            conversionsApplied.add("Converted custom page frontmatter fields to page.data.*");
        }

        return result;
    }

    private String makePageDataLenient(String content) {
        // page.data.* and post.data.* access a JsonObject — fields may not exist on every page.
        // Append ?? to make them lenient (resolve to null instead of throwing).
        // Skip if already lenient (??) or has a default (?:).
        Pattern pattern = Pattern.compile("((?<!-)(?:page|post)\\.data\\.[a-zA-Z0-9_.]+)(\\?\\?| \\?:)?");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(2) != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + "??"));
            }
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Made page.data.* references lenient");
        }
        return result;
    }

    private String convertUrlConcatenation(String content) {
        // RoqUrl doesn't support + operator. Convert url + something to url.resolve(something)
        // Patterns:
        // - site.url + page.url -> site.url.resolve(page.url)
        // - page.url + "/path" -> page.url.resolve("/path")

        // Match: (site.url or page.url) + (something)
        // We need to handle the full expression inside {=...}
        Pattern pattern = Pattern.compile(
                "(\\{=)([^}]*?)((?:site|page)\\.url)\\s*\\+\\s*([^}]+?)(\\})");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1); // {=
            String before = matcher.group(2); // anything before site.url
            String urlExpr = matcher.group(3); // site.url or page.url
            String arg = matcher.group(4).trim(); // what's being concatenated
            String suffix = matcher.group(5); // }

            String replacement = prefix + before + urlExpr + ".resolve(" + arg + ")" + suffix;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted URL concatenation to .resolve()");
        }

        return result;
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
            return sb.toString();
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
        return content;
    }

    private String appendRawToReplaceOutputs(String content) {
        // Jekyll never escapes HTML in output tags, but Qute auto-escapes in .html templates.
        // Append .raw to output expressions containing .replace() so HTML content renders correctly.
        Pattern pattern = Pattern.compile("(\\{=([^}]*\\.replace(?:All)?\\([^)]*\\)))\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + ".raw}"));
            found = true;
        }
        matcher.appendTail(sb);

        if (found) {
            conversionsApplied.add("Appended .raw to replace() outputs (Jekyll never escapes HTML)");
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
