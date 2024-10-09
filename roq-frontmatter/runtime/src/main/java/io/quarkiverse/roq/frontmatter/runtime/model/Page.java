package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

@TemplateData
@Vetoed
public interface Page {

    PageInfo info();

    default String id() {
        return info().resolvedPath();
    }

    default String rawContent() {
        return info().rawContent();
    }

    default String baseFileName() {
        return info().baseFileName();
    }

    default String sourceFileName() {
        return info().sourceFileName();
    }

    default String sourcePath() {
        return info().sourcePath();
    }

    default ZonedDateTime date() {
        return info().date();
    }

    default String title() {
        return data().getString("title");
    }

    default String description() {
        return data().getString("description");
    }

    default RoqUrl img() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return url().rootUrl().resolve(info().imagesRootPath()).resolve(img);
    }

    RoqUrl url();

    JsonObject data();

    default Object data(String name) {
        if (data().containsKey(name)) {
            return data().getValue(name);
        }
        return null;
    }

    static String getImgFromData(JsonObject data) {
        return data.getString("img", data.getString("image", data.getString("picture")));
    }

    static RoqUrl resolveImgUrl(RootUrl rootUrl, JsonObject data) {
        final String img = getImgFromData(data);
        if (img == null) {
            return null;
        }
        return rootUrl.resolve(img);
    }

}
