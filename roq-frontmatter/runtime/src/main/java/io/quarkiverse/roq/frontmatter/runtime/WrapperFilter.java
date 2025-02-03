package io.quarkiverse.roq.frontmatter.runtime;

import java.util.function.Function;

public record WrapperFilter(String prefix, String suffix) implements Function<String, String> {

    public static final WrapperFilter EMPTY = new WrapperFilter("", "");

    @Override
    public String apply(String s) {
        if (EMPTY.equals(this)) {
            return s;
        }
        return prefix() + s + suffix();
    }
}
