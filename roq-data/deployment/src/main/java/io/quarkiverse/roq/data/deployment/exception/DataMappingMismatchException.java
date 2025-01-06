
package io.quarkiverse.roq.data.deployment.exception;

public class DataMappingMismatchException extends RuntimeException {

    public DataMappingMismatchException(String message) {
        super(message);
    }

    public DataMappingMismatchException(String message, Throwable cause) {
        super(message, cause);
    }

}
