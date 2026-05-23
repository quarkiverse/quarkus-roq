package io.quarkiverse.roq.editor.runtime.devui;

public record WriteActionResult<T>(T result, String error, boolean async) {

    public static <T> WriteActionResult<T> success(T result) {
        return new WriteActionResult<>(result, null, true);
    }

    public static <T> WriteActionResult<T> sync(T result) {
        return new WriteActionResult<>(result, null, false);
    }

    public static <T> WriteActionResult<T> error(String error) {
        return new WriteActionResult<>(null, error, false);
    }
}
