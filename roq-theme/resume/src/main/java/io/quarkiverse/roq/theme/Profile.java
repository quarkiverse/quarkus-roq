package io.quarkiverse.roq.theme;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("profile")
public record Profile(
        @JsonProperty(required = true) String firstName,
        @JsonProperty(required = true) String lastName,
        String jobTitle,
        String bio,
        String city,
        String country,
        String phone,
        String email,
        String site) {
}
