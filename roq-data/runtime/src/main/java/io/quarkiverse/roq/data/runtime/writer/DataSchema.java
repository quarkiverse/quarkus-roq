package io.quarkiverse.roq.data.runtime.writer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Uses Jackson introspection to know, for a given Java type, which keys of a data object are bound to the type.
 * <p>
 * This is what lets the writer distinguish:
 * <ul>
 * <li>POJO/record objects: keys unknown to the type are kept untouched (the bean never saw them), and properties that
 * can't be deserialized (e.g. computed getters) are never written</li>
 * <li>maps: the data is the source of truth, missing entries are removed</li>
 * </ul>
 */
final class DataSchema {

    /**
     * Marker for map semantics.
     */
    static final Map<String, JavaType> MAP = Collections.unmodifiableMap(new LinkedHashMap<>());

    /**
     * Marker for a POJO type we could not introspect: keys present in the bean data are considered known.
     */
    static final Map<String, JavaType> LENIENT_POJO = Collections.unmodifiableMap(new LinkedHashMap<>());

    private final ObjectMapper mapper;
    private final Map<JavaType, Map<String, JavaType>> cache = new ConcurrentHashMap<>();

    DataSchema(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @return {@link #MAP}, {@link #LENIENT_POJO} or the deserializable properties of the type, by name
     */
    Map<String, JavaType> properties(JavaType type) {
        if (type == null || type.isJavaLangObject()) {
            // unknown type (e.g. erased generics): never write nulls, never drop keys
            return LENIENT_POJO;
        }
        if (type.isMapLikeType() || type.isContainerType() || type.isPrimitive()
                || JsonNode.class.isAssignableFrom(type.getRawClass())) {
            return MAP;
        }
        return cache.computeIfAbsent(type, this::introspect);
    }

    JavaType elementType(JavaType type) {
        if (type != null && (type.isCollectionLikeType() || type.isArrayType())) {
            return type.getContentType();
        }
        return null;
    }

    JavaType mapValueType(JavaType type) {
        if (type != null && type.isMapLikeType()) {
            return type.getContentType();
        }
        return null;
    }

    private Map<String, JavaType> introspect(JavaType type) {
        try {
            final BeanDescription description = mapper.getDeserializationConfig().introspect(type);
            if (description.findAnySetterAccessor() != null) {
                // Unknown keys are captured by the bean, the data is the source of truth
                return MAP;
            }
            final Map<String, JavaType> result = new LinkedHashMap<>();
            for (BeanPropertyDefinition property : description.findProperties()) {
                if (property.couldDeserialize()) {
                    result.put(property.getName(), property.getPrimaryType());
                }
            }
            return result.isEmpty() ? LENIENT_POJO : Collections.unmodifiableMap(result);
        } catch (RuntimeException e) {
            return LENIENT_POJO;
        }
    }
}
