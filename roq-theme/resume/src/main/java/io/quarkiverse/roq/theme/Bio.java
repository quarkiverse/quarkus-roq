package io.quarkiverse.roq.theme;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "bio", parentArray = true, required = true)
public record Bio(
        List<Section> list) {
    public record Section(@JsonProperty(required = true) String title, List<Item> items) {
    }

    public record Item(
            @JsonProperty(required = true) String header,
            @JsonProperty(required = true) String title,
            String link,
            String content) {
    }
}
