package io.quarkiverse.roq.data.test.util;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("why")
public record Bar(String style) {
}
