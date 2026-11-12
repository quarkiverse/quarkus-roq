package io.quarkiverse.roq.data.runtime;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.data.runtime.writer.DataTreeWriter;
import io.quarkus.arc.ClientProxy;

/**
 * Reverse mode of Roq data: writes a data object (typically a {@link DataMapping} bean) to YAML or JSON.
 * <p>
 * When the original content of the data file is provided, it is updated rather than regenerated:
 * <ul>
 * <li>YAML comments, key order, indentation and string quoting are preserved</li>
 * <li>JSON key order, indentation and line breaks are preserved</li>
 * <li>values which didn't change are left untouched</li>
 * <li>keys which are not bound to the Java type are left untouched</li>
 * </ul>
 * For a {@link DataMapping.Type#ARRAY_FILE} mapping, the list held by the bean is written as the root array.
 * <p>
 * This class doesn't access any file, see {@link DataMappingWriter} for that.
 */
public class DataSerializer {

    private final JsonMapper mapper;
    private final DataTreeWriter treeWriter;

    public DataSerializer() {
        this(null);
    }

    /**
     * @param propertyNamingStrategy the naming strategy used when reading data files (see
     *        {@code quarkus.roq.jackson.property-naming-strategy})
     */
    public DataSerializer(PropertyNamingStrategy propertyNamingStrategy) {
        final JsonMapper.Builder builder = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                // java.time values are written as ISO-8601 text, the way they are read
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (propertyNamingStrategy != null) {
            builder.propertyNamingStrategy(propertyNamingStrategy);
        }
        this.mapper = builder.build();
        this.treeWriter = new DataTreeWriter(mapper);
    }

    /**
     * Write the data from scratch.
     */
    public String write(Object data, DataFormat format) {
        return write(data, null, format);
    }

    /**
     * Write the data by updating the original content.
     *
     * @param data the data object or bean (client proxies are unwrapped)
     * @param original the original content of the data file, or {@code null} to write from scratch
     * @param format the format of the data file
     * @return the updated content
     */
    public String write(Object data, String original, DataFormat format) {
        Objects.requireNonNull(data, "data is required");
        Objects.requireNonNull(format, "format is required");
        final Object unwrapped = ClientProxy.unwrap(data);
        final Class<?> type = unwrapped.getClass();
        final DataMapping mapping = type.getAnnotation(DataMapping.class);
        final Object root;
        final JavaType rootType;
        if (mapping != null && isArrayFile(mapping)) {
            final BeanPropertyDefinition list = findListProperty(type);
            root = list.getAccessor().getValue(unwrapped);
            rootType = list.getPrimaryType();
        } else if (mapping != null && mapping.type() != DataMapping.Type.OBJECT_FILE) {
            throw new UnsupportedOperationException(
                    "Writing @DataMapping(type=%s) is not supported (%s)".formatted(mapping.type(), type.getName()));
        } else {
            root = unwrapped;
            rootType = mapper.getTypeFactory().constructType(type);
        }
        final JsonNode tree = mapper.valueToTree(root);
        return treeWriter.write(tree, rootType, original, format);
    }

    @SuppressWarnings("deprecation")
    private static boolean isArrayFile(DataMapping mapping) {
        return mapping.type() == DataMapping.Type.ARRAY_FILE
                || (mapping.type() == DataMapping.Type.OBJECT_FILE && mapping.parentArray());
    }

    /**
     * The list held by an ARRAY_FILE type, as Jackson sees it (record component, getter or public field).
     */
    private BeanPropertyDefinition findListProperty(Class<?> type) {
        final BeanDescription description = mapper.getSerializationConfig().introspect(mapper.constructType(type));
        final List<BeanPropertyDefinition> lists = description.findProperties().stream()
                .filter(p -> p.getAccessor() != null && List.class.isAssignableFrom(p.getRawPrimaryType()))
                .toList();
        if (lists.size() != 1) {
            throw new IllegalArgumentException(
                    "@DataMapping(type=ARRAY_FILE) class %s should expose exactly one List property to be written, found %d"
                            .formatted(type.getName(), lists.size()));
        }
        final BeanPropertyDefinition list = lists.getFirst();
        list.getAccessor().fixAccess(true);
        return list;
    }
}
