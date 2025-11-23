package io.quarkiverse.roq.theme;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "social", parentArray = true)
public record Social(
        List<Item> items) {

    public record Item(String type,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String url) {
    }
}
