package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.join;

public record RootUrl(String url, String rootPath) implements SiteUrl {

    @Override
    public RoqUrl relative() {
        return new RoqUrl(rootPath);
    }

    @Override
    public RoqUrl absolute() {
        return new RoqUrl(join(url, rootPath));
    }

    @Override
    public String toString() {
        return relative().toString();
    }
}
