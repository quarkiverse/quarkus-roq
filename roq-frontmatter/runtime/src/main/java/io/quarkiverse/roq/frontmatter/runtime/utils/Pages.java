package io.quarkiverse.roq.frontmatter.runtime.utils;

import static io.quarkiverse.roq.frontmatter.runtime.model.PageFiles.slugifyFile;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.vertx.core.json.JsonObject;

public final class Pages {
    private Pages() {
    }

    public static RoqUrl resolveFile(Page page, Object name, String missingResourceMessage,
            String notFoundMessage) {
        if (name == null) {
            return null;
        }
        if (page.source().hasNoFiles()) {
            throw new RoqStaticFileException(missingResourceMessage.formatted(name));
        }
        final String f = normaliseName(name, page.source().files().slugified());
        if (page.source().fileExists(f)) {
            return page.url().resolve(f);
        } else {
            throw new RoqStaticFileException(notFoundMessage.formatted(name,
                    String.join(", ", page.source().files().names())));
        }
    }

    public static String normaliseName(Object name, boolean slugify) {
        final String clean = String.valueOf(name).replace("./", "");
        if (slugify) {
            return slugifyFile(clean);
        } else {
            return clean;
        }

    }

    public static RoqUrl resolvePublicFile(Page page, Object name) {
        return resolveFile(page, name, "File '%s' not found in public dir (public dir is empty).",
                "File '%s' not found in public dir (found: %s).");
    }

    public static String getImgFromData(JsonObject data) {
        return data.getString("img", data.getString("image", data.getString("picture")));
    }

}
