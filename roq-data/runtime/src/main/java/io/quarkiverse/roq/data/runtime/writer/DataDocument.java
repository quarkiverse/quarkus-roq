package io.quarkiverse.roq.data.runtime.writer;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The original content of a data file, which can be written back with updated data.
 */
interface DataDocument {

    /**
     * @return the content as a data tree, {@code null} if the document is empty
     */
    JsonNode data();

    /**
     * @return the content updated with the given (merged) data
     */
    String write(JsonNode merged);
}
