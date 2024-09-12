package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkiverse.roq.util.PathUtils;

public interface RoqUrl {

    Resolvable relative();

    Resolvable absolute();

    default Resolvable relative(String path) {
        return relative().resolve(path);
    }

    default Resolvable absolute(String path) {
        return absolute().resolve(path);
    }

    record Resolvable(String path) {

        public Resolvable resolve(Object other) {
            return new Resolvable(PathUtils.join(path, other.toString()));
        }

        public Resolvable join(Object other) {
            return this.resolve(other);
        }

        @Override
        public String toString() {
            return path;
        }
    }

}
