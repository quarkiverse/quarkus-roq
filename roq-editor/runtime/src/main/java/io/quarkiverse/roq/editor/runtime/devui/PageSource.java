package io.quarkiverse.roq.editor.runtime.devui;

public record PageSource(
        String collectionId,
        String path,
        String title,
        String description,
        String url,
        String extension,
        String markup,
        String date,
        String suggestedPath) {
}
