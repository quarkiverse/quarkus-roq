package io.quarkiverse.roq.data.test.util;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("list")
public record ItemRecord(String name) {
}
