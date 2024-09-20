package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.join;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

public record RootUrl(String url, String quarkusRootPath) implements SiteUrl {

    public RootUrl(String url, String quarkusRootPath) {
        this.quarkusRootPath = removeTrailingSlash(quarkusRootPath);
        this.url = url;
    }

    @Override
    public RoqUrl relative() {
        return new RoqUrl(quarkusRootPath);
    }

    @Override
    public RoqUrl absolute() {
        return new RoqUrl(join(url, quarkusRootPath));
    }

    @Override
    public String toString() {
        return relative().toString();
    }

}
