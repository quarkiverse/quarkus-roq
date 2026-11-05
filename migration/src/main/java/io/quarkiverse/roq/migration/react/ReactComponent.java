package io.quarkiverse.roq.migration.react;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a React component file does, as far as a migration needs to know: whether it is a static template, what it
 * renders, and from what.
 *
 * @param file the file, relative to the migrated directory
 * @param name the name of the component the file exports by default; null if there is none
 * @param kind how the component behaves, which decides how it can be migrated
 * @param reasons what in the code made it that kind, e.g. {@code useState}, {@code onClick}
 * @param imports the names the file imports, and where from
 * @param props the props the component takes
 * @param prelude the code the component runs before returning its JSX, apart from build-time queries; empty if none
 * @param queries the variables holding the results of {@code useStaticQuery}, with their GraphQL query
 * @param pageQuery the GraphQL query a Gatsby page exports, whose result the page gets as its {@code data} prop; null
 *        if there is none
 * @param jsx the JSX the component returns; null if it could not be found
 * @param head the JSX of the {@code Head} a Gatsby page exports, which renders its {@code <head>}; null if there is
 *        none
 * @param styled the styled-components the file defines, by name
 */
public record ReactComponent(String file, String name, Kind kind, List<String> reasons, Map<String, Import> imports,
        Set<String> props, String prelude, Map<String, String> queries, String pageQuery, String jsx, String head,
        Map<String, Styled> styled) {

    public enum Kind {
        /**
         * Renders the same HTML every time from its props and build-time data: it can become a Qute template.
         */
        STATIC,
        /**
         * Responds to the user, with event handlers and usually state: React adds value.
         */
        INTERACTIVE,
        /**
         * Computes what it renders in the browser, e.g. by fetching data in an effect, and is static after that:
         * moving the computation to build time would add value, but takes rewriting.
         */
        COMPUTED_IN_BROWSER,
        /**
         * Static, but runs scripts that do not change what it renders, such as analytics: the scripts can go in the
         * layout.
         */
        SIDE_EFFECTS
    }

    /**
     * @param from the module, e.g. {@code gatsby} or {@code ./header}
     * @param imported the name in that module; {@code default} for a default import, {@code *} for a namespace
     */
    public record Import(String from, String imported) {

        public boolean isLocal() {
            return from.startsWith(".");
        }
    }

    /**
     * A component made with {@code styled-components}.
     *
     * @param base what it styles: an HTML tag such as {@code a}, or a component such as {@code FontAwesomeIcon}
     * @param css its CSS; null if it has interpolations ({@code ${...}}), which need JavaScript
     */
    public record Styled(String base, String css) {
    }

    static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:(.+?)\\s+from\\s+)?[\"']([^\"']+)[\"']");
    static final Pattern DEFAULT_EXPORT = Pattern.compile(
            "export\\s+default\\s+(?:function\\s+(\\w+)|(\\w+)\\s*;?\\s*$)", Pattern.MULTILINE);
    static final Pattern STYLED = Pattern.compile(
            "const\\s+(\\w+)\\s*=\\s*styled(?:\\.(\\w+)|\\(\\s*(\\w+)\\s*\\)|\\((\\s*\\w+\\s*=>\\s*<(\\w+)[^`]*?)\\))\\s*`([^`]*)`");
    static final Pattern QUERY = Pattern.compile(
            "const\\s+(\\w+|\\{[^}]*})\\s*=\\s*useStaticQuery\\(\\s*graphql\\s*`([^`]*)`\\s*\\)\\s*;?");
    static final Pattern PAGE_QUERY = Pattern.compile("export\\s+const\\s+\\w+\\s*=\\s*graphql\\s*`([^`]*)`");
    static final String HEAD = "Head";
    static final Pattern HANDLER = Pattern.compile("\\son[A-Z]\\w*\\s*=\\s*\\{");
    static final Pattern STATE = Pattern.compile("\\b(useState|useReducer)\\s*\\(");
    static final Pattern EFFECT = Pattern.compile("\\b(useEffect|useLayoutEffect)\\s*\\(");
    static final Pattern BROWSER = Pattern
            .compile("\\b(window|document|localStorage|sessionStorage|navigator)\\.|\\bfetch\\s*\\(");
    static final Pattern REF = Pattern.compile("\\buseRef\\s*\\(");

    /**
     * @param file the file, relative to the migrated directory
     * @param source its JavaScript
     */
    public static ReactComponent read(String file, String source) {
        String js = JsSource.stripComments(source);

        Map<String, Import> imports = new LinkedHashMap<>();
        Matcher importMatcher = IMPORT.matcher(js);
        while (importMatcher.find()) {
            if (importMatcher.group(1) != null) {
                readImportClause(importMatcher.group(1), importMatcher.group(2), imports);
            }
        }

        Map<String, Styled> styled = new LinkedHashMap<>();
        Matcher styledMatcher = STYLED.matcher(js);
        while (styledMatcher.find()) {
            String base = styledMatcher.group(2) != null ? styledMatcher.group(2)
                    : styledMatcher.group(3) != null ? styledMatcher.group(3) : styledMatcher.group(5);
            String css = styledMatcher.group(6);
            // Without the line break after the backtick, keeping the indentation of each line
            String rules = css.replaceFirst("^[ \\t]*\\R", "").stripTrailing();
            styled.put(styledMatcher.group(1), new Styled(base, css.contains("${") ? null : rules));
        }

        Map<String, String> queries = new LinkedHashMap<>();
        Matcher queryMatcher = QUERY.matcher(js);
        while (queryMatcher.find()) {
            queries.put(queryMatcher.group(1).replaceAll("\\s+", ""), queryMatcher.group(2));
        }

        String name = null;
        Matcher export = DEFAULT_EXPORT.matcher(js);
        if (export.find()) {
            name = export.group(1) != null ? export.group(1) : export.group(2);
        }
        Definition definition = name == null ? null : definition(js, name);
        Matcher pageQuery = PAGE_QUERY.matcher(js);
        Definition head = js.contains("export const " + HEAD) || js.contains("export function " + HEAD)
                ? definition(js, HEAD)
                : null;

        List<String> reasons = new ArrayList<>();
        Kind kind = classify(js, reasons);
        return new ReactComponent(file, name, kind, List.copyOf(reasons), imports,
                definition == null ? Set.of() : definition.props(),
                definition == null ? "" : definition.prelude(), queries, pageQuery.find() ? pageQuery.group(1) : null,
                definition == null ? null : definition.jsx(), head == null ? null : head.jsx(), styled);
    }

    static Kind classify(String js, List<String> reasons) {
        Set<String> handlers = found(HANDLER, js, m -> m.group().strip().replaceAll("\\s*=\\s*\\{$", ""));
        Set<String> state = found(STATE, js, m -> m.group(1));
        Set<String> effects = found(EFFECT, js, m -> m.group(1));
        Set<String> browser = found(BROWSER, js, m -> m.group().replaceAll("[.(\\s]+$", ""));
        Set<String> refs = found(REF, js, m -> "useRef");
        boolean script = js.contains("<Script");

        if (!handlers.isEmpty()) {
            reasons.add("event handlers (" + String.join(", ", handlers) + ")");
            if (!state.isEmpty()) {
                reasons.add("state (" + String.join(", ", state) + ")");
            }
            return Kind.INTERACTIVE;
        }
        if (!state.isEmpty() && (!effects.isEmpty() || !browser.isEmpty())) {
            reasons.add("state set from " + String.join(", ", union(effects, browser)));
            return Kind.COMPUTED_IN_BROWSER;
        }
        if (!state.isEmpty()) {
            reasons.add("state (" + String.join(", ", state) + ")");
            return Kind.INTERACTIVE;
        }
        if (!effects.isEmpty() || !browser.isEmpty() || !refs.isEmpty() || script) {
            Set<String> effectReasons = union(union(effects, browser), refs);
            if (script) {
                effectReasons.add("<Script>");
            }
            reasons.add("scripts that do not change what it renders (" + String.join(", ", effectReasons) + ")");
            return Kind.SIDE_EFFECTS;
        }
        return Kind.STATIC;
    }

    private record Definition(Set<String> props, String prelude, String jsx) {
    }

    /**
     * Finds {@code function Name(props) { ... return (<jsx>) }} or {@code const Name = (props) => (<jsx>)}, with or
     * without a block body.
     */
    private static Definition definition(String js, String name) {
        Matcher function = Pattern.compile("function\\s+" + Pattern.quote(name) + "\\s*\\(").matcher(js);
        Matcher arrow = Pattern.compile("const\\s+" + Pattern.quote(name) + "\\s*=\\s*(?:\\(|(\\w+)\\s*=>)").matcher(js);
        int paramsOpen;
        int bodyStart;
        String params;
        if (function.find()) {
            paramsOpen = function.end() - 1;
            int paramsClose = JsSource.matching(js, paramsOpen);
            params = js.substring(paramsOpen + 1, paramsClose);
            bodyStart = paramsClose + 1;
        } else if (arrow.find()) {
            if (arrow.group(1) != null) {
                params = arrow.group(1);
                bodyStart = arrow.end();
            } else {
                paramsOpen = arrow.end() - 1;
                int paramsClose = JsSource.matching(js, paramsOpen);
                params = js.substring(paramsOpen + 1, paramsClose);
                int arrowIndex = js.indexOf("=>", paramsClose);
                if (arrowIndex < 0) {
                    return null;
                }
                bodyStart = arrowIndex + 2;
            }
        } else {
            return null;
        }

        int i = skipSpace(js, bodyStart);
        if (i >= js.length()) {
            return null;
        }
        String prelude = "";
        String returned;
        if (js.charAt(i) == '{') {
            int bodyEnd = JsSource.matching(js, i);
            String body = js.substring(i + 1, bodyEnd);
            int ret = topLevelReturn(body);
            if (ret < 0) {
                return null;
            }
            prelude = QUERY.matcher(body.substring(0, ret)).replaceAll("").strip();
            returned = body.substring(ret + "return".length()).strip();
        } else {
            returned = js.substring(i);
            if (returned.startsWith("(")) {
                returned = returned.substring(0, JsSource.matching(returned, 0) + 1);
            }
        }
        String jsx = JsSource.unwrap(returned.replaceFirst(";\\s*$", ""));
        if (jsx.startsWith("<")) {
            try {
                // An arrow function's body without parentheses runs on to the rest of the file
                jsx = jsx.substring(0, Jsx.parse(jsx, 0).end());
            } catch (Jsx.JsxException e) {
                // Left for the conversion to say it cannot read it
            }
        }
        return new Definition(props(params), prelude, jsx.startsWith("<") ? jsx : null);
    }

    private static int topLevelReturn(String body) {
        Matcher ret = Pattern.compile("\\breturn\\b").matcher(body);
        while (ret.find()) {
            // At the top level of the body: as many opening as closing brackets before it
            String before = body.substring(0, ret.start());
            if (JsSource.indexAtTopLevel(before + "\u0000", "\u0000") == before.length()) {
                return ret.start();
            }
        }
        return -1;
    }

    /**
     * @return the props a component takes from {@code {a, b = 1, ...rest}}; a single props parameter gives none
     */
    private static Set<String> props(String params) {
        Set<String> props = new LinkedHashSet<>();
        String p = params.strip();
        if (p.startsWith("{") && p.endsWith("}")) {
            for (String prop : p.substring(1, p.length() - 1).split(",")) {
                String propName = prop.strip().replaceFirst("\\s*[=:].*$", "");
                if (!propName.isEmpty() && !propName.startsWith("...")) {
                    props.add(propName);
                }
            }
        }
        return props;
    }

    private static void readImportClause(String clause, String from, Map<String, Import> imports) {
        String c = clause.strip();
        int brace = c.indexOf('{');
        String defaultPart = brace < 0 ? c : c.substring(0, brace).replaceAll(",\\s*$", "").strip();
        if (defaultPart.startsWith("* as ")) {
            imports.put(defaultPart.substring(5).strip(), new Import(from, "*"));
        } else if (!defaultPart.isEmpty()) {
            imports.put(defaultPart, new Import(from, "default"));
        }
        if (brace >= 0) {
            for (String named : c.substring(brace + 1, c.indexOf('}', brace)).split(",")) {
                String[] parts = named.strip().split("\\s+as\\s+");
                if (!parts[0].isEmpty()) {
                    imports.put(parts.length > 1 ? parts[1] : parts[0], new Import(from, parts[0]));
                }
            }
        }
    }

    private static Set<String> found(Pattern pattern, String js, java.util.function.Function<Matcher, String> label) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(js);
        while (m.find()) {
            found.add(label.apply(m));
        }
        return found;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return union;
    }

    private static int skipSpace(String js, int i) {
        while (i < js.length() && Character.isWhitespace(js.charAt(i))) {
            i++;
        }
        return i;
    }
}
