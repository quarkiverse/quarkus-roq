package io.quarkiverse.roq.data.runtime.writer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Merges the data tree of a bean into the data tree of the original file.
 * <p>
 * Rules:
 * <ul>
 * <li>original key order is kept, new keys are inserted after the closest preceding key in the bean order</li>
 * <li>values that are semantically unchanged are kept as-is (e.g. {@code 1.0} vs {@code "1.0"})</li>
 * <li>for POJOs, keys that the type doesn't bind are kept, and a {@code null} property removes the key</li>
 * <li>for maps, the bean is the source of truth</li>
 * <li>lists are aligned element by element (see {@link Alignment})</li>
 * </ul>
 * When nothing changed in a subtree, the original node instance is returned.
 */
final class TreeMerger {

    private final DataSchema schema;
    private final JsonNodeFactory factory = JsonNodeFactory.instance;

    TreeMerger(DataSchema schema) {
        this.schema = schema;
    }

    JsonNode merge(JsonNode original, JsonNode updated, JavaType type) {
        if (original == null || original.isMissingNode()) {
            return fresh(updated, type);
        }
        if (same(original, updated)) {
            return original;
        }
        if (original.isObject() && updated.isObject()) {
            return mergeObject((ObjectNode) original, (ObjectNode) updated, type);
        }
        if (original.isArray() && updated.isArray()) {
            return mergeArray((ArrayNode) original, (ArrayNode) updated, schema.elementType(type));
        }
        return fresh(updated, type);
    }

    private JsonNode mergeObject(ObjectNode original, ObjectNode updated, JavaType type) {
        final Map<String, JavaType> properties = schema.properties(type);
        final boolean isMap = properties == DataSchema.MAP;
        final List<String> keys = new ArrayList<>();
        final Map<String, JsonNode> values = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = original.fields(); it.hasNext();) {
            final Map.Entry<String, JsonNode> entry = it.next();
            final String key = entry.getKey();
            final JsonNode originalValue = entry.getValue();
            final JsonNode updatedValue = updated.get(key);
            final JsonNode value;
            if (isMap) {
                value = updatedValue == null ? null : merge(originalValue, updatedValue, schema.mapValueType(type));
            } else if (!isKnown(properties, updated, key)) {
                // not bound to the bean, keep it as-is
                value = originalValue;
            } else if (updatedValue == null || updatedValue.isNull()) {
                // the property was unset, remove the key (unless it was already null)
                value = originalValue.isNull() ? originalValue : null;
            } else {
                value = merge(originalValue, updatedValue, properties.get(key));
            }
            if (value != null) {
                keys.add(key);
                values.put(key, value);
            }
        }
        // new keys are inserted after the closest preceding key, in the bean order
        String previous = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = updated.fields(); it.hasNext();) {
            final Map.Entry<String, JsonNode> entry = it.next();
            final String key = entry.getKey();
            if (original.has(key)) {
                if (values.containsKey(key)) {
                    previous = key;
                }
                continue;
            }
            final JsonNode value;
            if (isMap) {
                value = fresh(entry.getValue(), schema.mapValueType(type));
            } else if (isKnown(properties, updated, key) && !entry.getValue().isNull()) {
                value = fresh(entry.getValue(), properties.get(key));
            } else {
                continue;
            }
            keys.add(previous == null ? 0 : keys.indexOf(previous) + 1, key);
            values.put(key, value);
            previous = key;
        }
        final ObjectNode result = factory.objectNode();
        for (String key : keys) {
            result.set(key, values.get(key));
        }
        return result;
    }

    private JsonNode mergeArray(ArrayNode original, ArrayNode updated, JavaType elementType) {
        final int[] alignment = Alignment.align(original.size(), updated.size(),
                (i, j) -> same(original.get(i), updated.get(j)),
                (i, j) -> similarity(original.get(i), updated.get(j)));
        final ArrayNode result = factory.arrayNode(updated.size());
        for (int j = 0; j < updated.size(); j++) {
            final int i = alignment[j];
            result.add(i < 0 ? fresh(updated.get(j), elementType) : merge(original.get(i), updated.get(j), elementType));
        }
        return result;
    }

    /**
     * Prepare a subtree coming from the bean only: drop null and non-deserializable POJO properties.
     */
    JsonNode fresh(JsonNode updated, JavaType type) {
        if (updated.isObject()) {
            final Map<String, JavaType> properties = schema.properties(type);
            final boolean isMap = properties == DataSchema.MAP;
            final ObjectNode result = factory.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = updated.fields(); it.hasNext();) {
                final Map.Entry<String, JsonNode> entry = it.next();
                if (isMap) {
                    result.set(entry.getKey(), fresh(entry.getValue(), schema.mapValueType(type)));
                } else if (!entry.getValue().isNull() && isKnown(properties, updated, entry.getKey())) {
                    result.set(entry.getKey(), fresh(entry.getValue(), properties.get(entry.getKey())));
                }
            }
            return result;
        }
        if (updated.isArray()) {
            final JavaType elementType = schema.elementType(type);
            final ArrayNode result = factory.arrayNode(updated.size());
            for (JsonNode element : updated) {
                result.add(fresh(element, elementType));
            }
            return result;
        }
        return updated;
    }

    private static boolean isKnown(Map<String, JavaType> properties, JsonNode updated, String key) {
        return properties == DataSchema.LENIENT_POJO ? updated.has(key) : properties.containsKey(key);
    }

    /**
     * Semantic equality: numbers are compared by value, scalars by their text (so {@code "1"} equals {@code 1}, which
     * is what Jackson would bind anyway).
     */
    static boolean same(JsonNode a, JsonNode b) {
        if (a == b) {
            return true;
        }
        // Jackson handles objects and arrays, the comparator is only used for the other nodes
        return a != null && b != null && a.equals(SAME_VALUE, b);
    }

    private static final Comparator<JsonNode> SAME_VALUE = (a, b) -> sameValue(a, b) ? 0 : 1;

    private static boolean sameValue(JsonNode a, JsonNode b) {
        if (!a.isValueNode() || !b.isValueNode()) {
            return false;
        }
        if (a.isNull() || b.isNull()) {
            return a.isNull() && b.isNull();
        }
        if (a.isNumber() && b.isNumber()) {
            return sameNumber(a, b);
        }
        return a.asText().equals(b.asText());
    }

    /**
     * Used to pair modified list elements: {@code 0} when the kinds differ, otherwise 1 + the number of equal fields.
     */
    static int similarity(JsonNode a, JsonNode b) {
        if (a.isObject() && b.isObject()) {
            int score = 1;
            for (Iterator<Map.Entry<String, JsonNode>> it = a.fields(); it.hasNext();) {
                final Map.Entry<String, JsonNode> entry = it.next();
                if (same(entry.getValue(), b.get(entry.getKey()))) {
                    score++;
                }
            }
            return score;
        }
        if ((a.isArray() && b.isArray()) || (a.isValueNode() && b.isValueNode())) {
            return 1;
        }
        return 0;
    }

    private static boolean sameNumber(JsonNode a, JsonNode b) {
        if (a.isFloat() || b.isFloat()) {
            return Float.compare(a.floatValue(), b.floatValue()) == 0;
        }
        try {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        } catch (NumberFormatException e) {
            // NaN / Infinity
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
    }
}
