package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.util.List;

public interface DataConverter {

    Object convert(byte[] content) throws IOException;

    <T> T convertToType(byte[] content, Class<T> clazz) throws IOException;

    <T> List<T> convertToTypedList(byte[] content, Class<T> clazz) throws IOException;
}
