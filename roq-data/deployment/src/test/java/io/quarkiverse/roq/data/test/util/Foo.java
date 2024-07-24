package io.quarkiverse.roq.data.test.util;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("foo")
public record Foo(String name) {
    @Override
    public String toString() {
        // Original is Foo[name=Super Heroes from Json]
        return this.name;
    }
}
