package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * An empty page implementation that safely returns null or empty values for all methods.
 * This prevents NullPointerExceptions when templates try to access properties on non-existent pages.
 */
@TemplateData
@Vetoed
final class EmptyPage extends Page {

    static final EmptyPage INSTANCE = new EmptyPage();

    private EmptyPage() {
        super(
                RoqUrl.fromPath("", ""),
                new PageSource(null, false, null, null, false),
                new JsonObject());
    }

    @Override
    public String id() {
        return null;
    }

    @Override
    public RoqUrl url() {
        return RoqUrl.fromPath("", "");
    }

    @Override
    public String title() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public Img image() {
        return null;
    }

    @Override
    public Img image(String path) {
        return null;
    }

    @Override
    public RoqUrl file(String path) {
        return null;
    }

    @Override
    public ZonedDateTime date() {
        return null;
    }

    @Override
    public Object data(String key) {
        return null;
    }

    @Override
    public JsonObject data() {
        return new JsonObject();
    }

    @Override
    public String content() {
        return "";
    }

    @Override
    public String rawContent() {
        return "";
    }

    @Override
    public PageSource source() {
        return new PageSource(null, false, null, null, false);
    }

    @Override
    public List<Page> paginate() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "EmptyPage{}";
    }
}