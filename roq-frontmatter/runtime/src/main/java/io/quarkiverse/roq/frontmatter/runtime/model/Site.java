package io.quarkiverse.roq.frontmatter.runtime.model;

import io.vertx.core.json.JsonObject;

public record Site(RootUrl rootUrl, RoqUrl url, RoqUrl imagesUrl, JsonObject data, java.util.List<NormalPage> pages,
        RoqCollections collections) {

    public String title() {
        return data().getString("title");
    }

    public String description() {
        return data().getString("description");
    }

    public RoqUrl img() {
        final String img = Page.getImgFromData(data());
        if (img == null) {
            return null;
        }
        return imagesUrl().resolve(img);
    }

}