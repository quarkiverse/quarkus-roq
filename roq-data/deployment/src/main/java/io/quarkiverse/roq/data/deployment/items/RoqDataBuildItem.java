package io.quarkiverse.roq.data.deployment.items;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item representing a Roq data file.
 */
public final class RoqDataBuildItem extends MultiBuildItem {

    /**
     * The name of the Roq data file.
     */
    private final String name;

    /**
     * The content of the Roq data file.
     */
    private final byte[] content;

    /**
     * The converter for this data
     */
    private final DataConverter converter;

    public RoqDataBuildItem(String name, byte[] content, DataConverter converter) {
        this.name = name;
        this.content = content;
        this.converter = converter;
    }

    public String getName() {
        return name;
    }

    public byte[] getContent() {
        return content;
    }

    public Object convert() throws IOException {
        return this.converter.convert(content);
    }

    public DataConverter converter() {
        return converter;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        RoqDataBuildItem that = (RoqDataBuildItem) object;
        return Objects.equals(name, that.name) && Arrays.equals(content, that.content)
                && Objects.equals(converter, that.converter);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, converter);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
