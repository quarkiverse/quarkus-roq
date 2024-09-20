package io.quarkiverse.roq.frontmatter.runtime;

public interface SiteUrl {

    RoqUrl relative();

    RoqUrl absolute();

    default RoqUrl relative(Object path) {
        return relative().resolve(path);
    }

    default RoqUrl absolute(Object path) {
        return absolute().resolve(path);
    }

}
