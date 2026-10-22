package io.quarkiverse.roq.frontmatter.runtime.model;

import java.io.Serializable;
import java.util.Map;

import io.quarkiverse.roq.LargeRecords;
import io.vertx.core.json.JsonObject;

/**
 * Serializable representation of any page type (normal or document).
 * Wraps page constructor arguments with {@link JsonObject} stored as its underlying
 * {@code Map<String, Object>} (which is fully serializable).
 */
public record SerializedPage(
        String type,
        RoqUrl url,
        PageSource source,
        Map<String, Object> data,
        Paginator paginator,
        String collectionId,
        boolean hidden) implements Serializable {

    private static final String NORMAL = "normal";
    private static final String DOCUMENT = "document";

    public static final Class<?>[] SERIALIZABLE_CLASSES = {
            SerializedPage.class, RoqUrl.class, RootUrl.class, PageSource.class,
            TemplateSource.class, SourceFile.class, PageFiles.class, Paginator.class
    };

    public static SerializedPage ofNormal(RoqUrl url, PageSource source, JsonObject data, Paginator paginator) {
        return new SerializedPage(NORMAL, url, source, LargeRecords.toSerializable(data), paginator, null, false);
    }

    public static SerializedPage ofDocument(String collectionId, RoqUrl url, PageSource source, JsonObject data,
            boolean hidden) {
        return new SerializedPage(DOCUMENT, url, source, LargeRecords.toSerializable(data), null, collectionId, hidden);
    }

    public Page toPage() {
        JsonObject jsonData = new JsonObject(data);
        return switch (type) {
            case NORMAL -> new NormalPage(url, source, jsonData, paginator);
            case DOCUMENT -> new DocumentPage(collectionId, url, source, jsonData, hidden);
            default -> throw new IllegalStateException("Unknown page type: " + type);
        };
    }
}
