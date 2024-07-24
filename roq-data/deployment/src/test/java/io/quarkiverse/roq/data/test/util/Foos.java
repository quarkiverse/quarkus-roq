package io.quarkiverse.roq.data.test.util;

import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "foos", parentArray = true)
public record Foos(List<Foo> list) {
}
