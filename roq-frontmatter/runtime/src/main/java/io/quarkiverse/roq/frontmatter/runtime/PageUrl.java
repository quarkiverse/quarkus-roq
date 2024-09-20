package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

public record PageUrl(SiteUrl parent, String path) implements SiteUrl {

    public PageUrl(SiteUrl parent, String path) {
        this.path = removeTrailingSlash(path);
        this.parent = parent;
    }

    @Override
    public RoqUrl relative() {
        return parent.relative(path);
    }

    @Override
    public RoqUrl absolute() {
        return parent.absolute(path);
    }

    @Override
    public String toString() {
        return relative().toString();
    }
}
