package io.quarkiverse.roq.frontmatter.runtime;

public record PageUrl(RootUrl root, String path) implements RoqUrl {

    @Override
    public Resolvable relative() {
        return root.relative().resolve(path);
    }

    @Override
    public Resolvable absolute() {
        return root.absolute().resolve(path);
    }

    @Override
    public String toString() {
        return path;
    }
}
