package io.quarkiverse.roq.data.test.util;

import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "heroes", type = DataMapping.Type.ARRAY_DIR)
public record HeroList(List<Hero> list) {
}
