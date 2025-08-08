package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Objects;
import java.util.StringJoiner;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a normal "standalone" page (not in a collection)
 */
@TemplateData
@Vetoed
public final class NormalPage extends Page {

    private final Paginator paginator;

    /**
     * @param url the url to this page
     * @param source the page info
     * @param data the FM data of this page
     * @param paginator the paginator if any
     */
    public NormalPage(RoqUrl url, PageSource source, JsonObject data, Paginator paginator) {
        super(url, source, data);
        this.paginator = paginator;
    }

    public Paginator paginator() {
        return paginator;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        NormalPage that = (NormalPage) o;
        return Objects.equals(paginator, that.paginator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), paginator);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", NormalPage.class.getSimpleName() + "[", "]")
                .add("paginator=" + paginator)
                .toString();
    }
}
