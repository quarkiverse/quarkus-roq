package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.join;

public record RootUrl(String url, String rootPath) implements RoqUrl {

    @Override
    public Resolvable relative() {
        return new Resolvable(rootPath);
    }

    @Override
    public Resolvable absolute() {
        return new Resolvable(join(url, rootPath));
    }

    @Override
    public String toString() {
        return relative().toString();
    }
}
