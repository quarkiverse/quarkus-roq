package io.quarkus.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiquidToQuteConverter {

    private final List<String> conversionsApplied = new ArrayList<>();

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
        content = convertConditionals(content);
        content = convertLoops(content);
        content = convertIncludes(content);
        content = convertAssignments(content);
        content = convertCaseStatements(content);
        content = convertLayoutTags(content);
        content = convertSpecialTags(content);

        content = convertBracketNotation(content);

        // Final cleanup steps - ORDER MATTERS!
        // Remove spaces first so ternary wrapping can match properly
        content = removeSpacesBeforeMethods(content);
        content = wrapTernaryBeforeMethods(content);

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

            // Convert post.* to page.* (Roq uses page for all content)
            var = var.replaceAll("\\bpost\\.", "page.");

            matcher.appendReplacement(sb, "{=" + Matcher.quoteReplacement(var) + "}");
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted variable outputs to alternative expression syntax");
        }

        return result;
    }

    private String convertFilters(String content) {
        Pattern blockPattern = Pattern.compile("\\{=[^}]*\\}|\\{%[^%]*%\\}");
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
        block = convertConcatenationFilters(block);
        block = convertTableDrivenFilters(block);
        block = convertDateFilter(block);
        block = convertDefaultFilter(block);
        block = convertTwoArgFilters(block);
        block = convertPushFilter(block);
        block = convertSplitFilter(block);
        return block;
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

        // Prepend filter: path | prepend: base -> base + path
        Pattern prependPattern = Pattern.compile("([a-zA-Z0-9_.\"']+)\\s*\\|\\s*prepend:\\s*([^|}%]+)");
        Matcher prependMatcher = prependPattern.matcher(content);
        StringBuilder prependSb = new StringBuilder();

        while (prependMatcher.find()) {
            String base = prependMatcher.group(1).trim();
            String prefix = prependMatcher.group(2).trim();
            prependMatcher.appendReplacement(prependSb, Matcher.quoteReplacement(prefix + " + " + base));
        }
        prependMatcher.appendTail(prependSb);

        result = prependSb.toString();
        if (!result.equals(content)) {
            conversionsApplied.add("Converted prepend filter to string concatenation");
            content = result;
        }

        return content;
    }

    private String convertTableDrivenFilters(String content) {
        // Filters with a single argument: | filter: arg -> .method(arg)
        String[][] filterWithArgMap = {
                {"sort", "sort"},
                {"startswith", "startsWith"},
                {"endswith", "endsWith"},
                {"contains", "contains"},
                {"equals", "equals"},
                {"map", "map"},
                {"group_by", "groupBy"},
                {"slice", "slice"},
                {"add", "add"},
                {"minus", "minus"},
                {"times", "times"},
                {"truncate", "truncate"},
                {"remove_first", "removeFirst"},
                {"where_exp", "whereExp"},
        };

        for (String[] mapping : filterWithArgMap) {
            String liquidFilter = mapping[0];
            String quteMethod = mapping[1];
            Pattern fwaPattern = Pattern.compile("\\|\\s*" + liquidFilter + ":\\s*([^|}%]+)");
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
                {"upcase", "toUpperCase"},
                {"downcase", "toLowerCase"},
                {"capitalize", "capitalize"},
                {"strip_html", "stripHtml"},
                {"number_of_words", "numberOfWords"},
                {"size", "size"},
                {"first", "first"},
                {"last", "last"},
                {"join", "join"},
                {"sort", "sort"},
                {"reverse", "reverse"},
                {"uniq", "distinct"},
                {"compact", "filterNotNull"},
                {"strip", "trim()"},
                {"lstrip", "trimStart"},
                {"rstrip", "trimEnd"},
                {"escape", "escapeHtml"},
                {"url_encode", "urlEncode"},
                {"slugify", "slugify"}
        };

        for (String[] mapping : filterMap) {
            String liquidFilter = mapping[0];
            String quteFilter = mapping[1];
            Pattern pattern = Pattern.compile("\\|\\s*" + liquidFilter + "\\b");
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
        Pattern datePattern = Pattern.compile("\\|\\s*date:\\s*[\"']([^\"']+)[\"']");
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
        Pattern defaultPattern = Pattern.compile("\\|\\s*default:\\s*([\"'][^\"']*[\"']|\\S+)");
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

    private String convertTwoArgFilters(String content) {
        // Truncatewords: | truncatewords: 50 -> .wordLimit(50)
        Pattern truncatePattern = Pattern.compile("\\|\\s*truncatewords:\\s*(\\d+)");
        String result = truncatePattern.matcher(content).replaceAll(".wordLimit($1)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted truncate filter");
            content = result;
        }

        // Replace_regex (must be before replace to avoid partial match)
        Pattern replaceRegexPattern = Pattern.compile("\\|\\s*replace_regex:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = replaceRegexPattern.matcher(content).replaceAll(".replaceAll($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted replace_regex filter");
            content = result;
        }

        // Replace
        Pattern replacePattern = Pattern.compile("\\|\\s*replace:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = replacePattern.matcher(content).replaceAll(".replace($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted replace filter");
            content = result;
        }

        // Where: | where: "key", "value" -> .where("key", "value")
        Pattern wherePattern = Pattern.compile("\\|\\s*where:\\s*(['\"][^'\"]*['\"])\\s*,\\s*(['\"][^'\"]*['\"])");
        result = wherePattern.matcher(content).replaceAll(".where($1, $2)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted where filter");
            content = result;
        }

        return content;
    }

    private String convertPushFilter(String content) {
        Pattern pushPattern = Pattern.compile("\\|\\s*push:\\s*([^}|%]+)");
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
        Pattern splitPattern = Pattern.compile("\\|\\s*split:\\s*(['\"][^'\"]*['\"])");
        String result = splitPattern.matcher(content).replaceAll(".split($1)");
        if (!result.equals(content)) {
            conversionsApplied.add("Converted split filter");
            content = result;
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

        // unless (negative if)
        content = content.replaceAll("\\{%\\s*unless\\s+([^%]+?)\\s*%\\}", "{#if !($1)}");
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

    private String convertLoops(String content) {
        String original = content;

        // Basic for loop
        content = content.replaceAll("\\{%\\s*for\\s+(\\w+)\\s+in\\s+([^%]+?)\\s*%\\}", "{#for $1 in $2}");
        content = content.replaceAll("\\{%\\s*endfor\\s*%\\}", "{/for}");

        // Loop variables: replace forloop.* with Qute metadata derived from the actual loop variable name.
        // We find each {#for VAR in ...} and replace forloop.* references with VAR_* in the loop body.
        content = replaceLoopVariables(content);

        // Handle limit and offset
        Pattern limitPattern = Pattern.compile("\\{#for\\s+(\\w+)\\s+in\\s+([^}]+?)\\s+limit:(\\d+)(?:\\s+offset:(\\d+))?\\s*\\}");
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
            String params = includeMatcher.group(2).trim();

            String replacement;
            if (!params.isEmpty()) {
                replacement = "{#include " + file + " " + params + " /}";
            } else {
                replacement = "{#include " + file + " /}";
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

    private String convertAssignments(String content) {
        String original = content;

        // Convert post.* to page.* inside assign tags before converting the tags themselves
        content = content.replaceAll("(\\{%\\s*assign\\s+\\w+\\s*=\\s*[^%]*?)\\bpost\\.", "$1page.");

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
        content = content.replaceAll("\\{%\\s*capture\\s+(\\w+)\\s*%\\}", "{#let $1}");
        content = content.replaceAll("\\{%\\s*endcapture\\s*%\\}", "{/let}");

        if (!content.equals(original)) {
            conversionsApplied.add("Converted assignments");
        }
        return content;
    }

    private int findScopeBoundary(String content, int startPos) {
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

        if (!content.equals(original)) {
            conversionsApplied.add("Converted layout tags");
        }
        return content;
    }

    private String convertSpecialTags(String content) {
        // Highlight blocks (for code syntax highlighting)
        // Liquid: {% highlight lang %}...{% endhighlight %}
        // Qute: <pre><code class="language-lang">...</code></pre>
        Pattern highlightPattern = Pattern.compile("\\{%\\s*highlight\\s+(\\w+)\\s*%\\}(.*?)\\{%\\s*endhighlight\\s*%\\}", Pattern.DOTALL);
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
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\.\\[\\]]+)\\s*\\?:\\s*([\"'][^\"']*[\"']|[a-zA-Z0-9_\\.\\[\\]]+)\\.([a-zA-Z0-9_]+)\\s*\\(");
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
        // Match Qute expressions: {=...}, {#...}, {/...}, {!...!}
        Pattern exprPattern = Pattern.compile("\\{[=#/!][^}]*\\}");
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
}
