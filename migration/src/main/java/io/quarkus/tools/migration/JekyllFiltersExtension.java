package io.quarkus.tools.migration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@TemplateExtension
public class JekyllFiltersExtension {

    /**
     * Null-safe get for JsonObject. Prevents NPE when key is null (e.g. from ?? operator).
     */
    static Object get(JsonObject obj, String key) {
        if (obj == null || key == null || key.isEmpty()) {
            return null;
        }
        return obj.getValue(key);
    }

    /**
     * Jekyll's "where" filter: select items from an array where a property matches a value.
     * Usage in Qute: {myArray.where("key", "value")}
     */
    static JsonArray where(JsonArray array, String property, String value) {
        JsonArray result = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.getValue(i);
            if (item instanceof JsonObject obj) {
                Object propValue = obj.getValue(property);
                if (propValue != null && propValue.toString().equals(value)) {
                    result.add(obj);
                }
            }
        }
        return result;
    }

    /**
     * Get the first element of a JsonArray.
     * Usage in Qute: {myArray.first}
     */
    static Object first(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return array.getValue(0);
    }

    /**
     * Get the last element of a JsonArray.
     * Usage in Qute: {myArray.last}
     */
    static Object last(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return array.getValue(array.size() - 1);
    }

    /**
     * Get the size of a JsonArray.
     * Usage in Qute: {myArray.size}
     */
    static int size(JsonArray array) {
        return array == null ? 0 : array.size();
    }

    /**
     * Jekyll's "truncate" filter: truncate a string to a given number of characters.
     * Usage in Qute: {myString.truncate(280)}
     */
    static String truncate(String str, int length) {
        if (str == null)
            return "";
        if (str.length() <= length)
            return str;
        return str.substring(0, length) + "...";
    }

    /**
     * Jekyll's "sort" filter: sort a list by a named property.
     * Usage in Qute: {myList.sort('title')}
     */
    static List<?> sort(List<?> list, String property) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Object> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            String va = extractProperty(a, property);
            String vb = extractProperty(b, property);
            if (va == null)
                return vb == null ? 0 : 1;
            if (vb == null)
                return -1;
            return va.compareToIgnoreCase(vb);
        });
        return sorted;
    }

    @TemplateExtension(namespace = "list")
    static Object whereExp(Iterable<?> items, String loopVar, Object conditionsObj) {
        if (items == null) {
            return new ArrayList<>();
        }

        List<String> conditions;
        if (conditionsObj instanceof String s) {
            conditions = List.of(s);
        } else if (conditionsObj instanceof Iterable<?> iter) {
            conditions = new ArrayList<>();
            for (Object o : iter) {
                conditions.add(o.toString());
            }
        } else {
            conditions = List.of(conditionsObj.toString());
        }

        String prefix = loopVar + ".";
        boolean isJsonArray = items instanceof JsonArray;
        List<Object> result = isJsonArray ? new JsonArray().getList() : new ArrayList<>();

        for (Object item : items) {
            boolean matches = true;
            for (String condition : conditions) {
                if (!evaluateCondition(item, prefix, condition)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(item);
            }
        }
        return isJsonArray ? new JsonArray(result) : result;
    }

    private static boolean evaluateCondition(Object item, String prefix, String condition) {
        String[] operators = { ">=", "<=", "!=", "==", ">", "<", "contains" };

        for (String op : operators) {
            int idx = condition.indexOf(" " + op + " ");
            if (idx >= 0) {
                String left = condition.substring(0, idx).trim();
                String right = condition.substring(idx + op.length() + 2).trim();

                String property = left.startsWith(prefix) ? left.substring(prefix.length()) : left;

                if ((right.startsWith("'") && right.endsWith("'"))
                        || (right.startsWith("\"") && right.endsWith("\""))) {
                    right = right.substring(1, right.length() - 1);
                }

                Object propValue = getProperty(item, property);
                String propStr = propValue != null ? propValue.toString() : null;

                return compareValues(propStr, op, right);
            }
        }
        return false;
    }

    private static boolean compareValues(String left, String op, String right) {
        if (left == null)
            return false;
        int cmp = left.compareTo(right);
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case "contains" -> left.contains(right);
            default -> false;
        };
    }

    private static Object getProperty(Object obj, String property) {
        if (obj instanceof JsonObject json) {
            return json.getValue(property);
        }
        if (obj instanceof java.util.Map<?, ?> map) {
            return map.get(property);
        }
        Class<?> clazz = obj.getClass();
        for (String p : new String[] { "get", "is", "" }) {
            String methodName = p.isEmpty() ? property
                    : p + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            try {
                Method m = clazz.getMethod(methodName);
                return m.invoke(obj);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String extractProperty(Object obj, String property) {
        if (obj instanceof JsonObject json) {
            Object val = json.getValue(property);
            return val != null ? val.toString() : null;
        }
        if (obj instanceof java.util.Map<?, ?> map) {
            Object val = map.get(property);
            return val != null ? val.toString() : null;
        }
        return obj != null ? obj.toString() : null;
    }

    /**
     * Jekyll's "push" filter: append an element to a list and return the new list.
     * Usage in Qute: {myList.push(item)}
     */
    static List<Object> push(List<?> list, Object item) {
        List<Object> result = new ArrayList<>(list);
        result.add(item);
        return result;
    }

    /**
     * Output a string without HTML escaping.
     * Qute auto-escapes HTML in .html templates; this bypasses that for trusted content.
     * Usage in Qute: {=myString.raw}
     */
    static RawString raw(String str) {
        if (str == null)
            return new RawString("");
        return new RawString(str);
    }

    /**
     * Split a string by delimiter, returning an iterable list.
     * Uses namespace form so it can handle null base objects (instance extensions can't
     * dispatch on null). Also returns List instead of String[] for Qute iteration.
     * Usage in Qute: {str:split(myString, ",")}
     */
    @TemplateExtension(namespace = "str")
    static List<String> split(String str, String delimiter) {
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(str.split(delimiter));
    }

    /**
     * Split, trim each element, and filter out empty strings.
     * Replaces the Liquid pattern: assign clean = "" | split: "" / for x in raw / push trimmed / endfor
     * That pattern doesn't work in Qute because {#let} is block-scoped (push results are discarded).
     * Usage in Qute: {str:splitTrimmed(myString, ",")}
     */
    @TemplateExtension(namespace = "str")
    static List<String> splitTrimmed(String str, String delimiter) {
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String s : str.split(delimiter)) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
