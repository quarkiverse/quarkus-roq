package io.quarkiverse.roq.frontmatter.runtime;

public record PageUrl(RootUrl root, String path) implements SiteUrl {

    @Override
    public RoqUrl relative() {
        return root.relative().resolve(path);
    }

    @Override
    public RoqUrl absolute() {
        return root.absolute().resolve(path);
    }

    @Override
    public String toString() {
        return relative().toString();
    }
}
