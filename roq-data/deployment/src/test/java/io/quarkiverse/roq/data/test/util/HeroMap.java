package io.quarkiverse.roq.data.test.util;

import java.util.Map;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "heroes", type = DataMapping.Type.OBJECT_DIR)
public record HeroMap(Map<String, Hero> map) {
}
