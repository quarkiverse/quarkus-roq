package io.quarkiverse.roq.frontmatter.runtime.utils;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.IMAGE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.IMG;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.PICTURE;
import static io.quarkiverse.roq.frontmatter.runtime.model.PageFiles.slugifyFile;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.vertx.core.json.JsonObject;

public final class Pages {
    private Pages() {
    }

    public static RoqUrl resolveFile(Page page, Object name, String fileContext) {
        if (name == null) {
            return null;
        }
        if (page.source().hasNoFiles()) {
            throw new RoqStaticFileException(RoqException.builder("File not found")
                    .source(page.source().template())
                    .detail("'%s' not found in %s (directory is empty).".formatted(name, fileContext))
                    .hint("Add the file to the page directory or check the file name."));
        }
        final String f = normaliseName(name, page.source().files().slugified());
        if (page.source().fileExists(f)) {
            return page.url().resolve(f);
        } else {
            throw new RoqStaticFileException(RoqException.builder("File not found")
                    .source(page.source().template())
                    .detail("'%s' not found in %s.".formatted(name, fileContext))
                    .hint("Available files: %s".formatted(String.join(", ", page.source().files().names()))));
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
        return resolveFile(page, name, "the public directory");
    }

    public static String getImgFromData(JsonObject data) {
        return data.getString(IMG, data.getString(IMAGE, data.getString(PICTURE)));
    }

}
