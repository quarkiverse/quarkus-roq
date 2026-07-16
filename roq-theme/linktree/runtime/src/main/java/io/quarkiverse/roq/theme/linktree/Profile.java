package io.quarkiverse.roq.theme.linktree;

import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("profile")
public record Profile(String name, String handle, String title, String bio,
        String image, List<Social> social, List<Link> links) {

    public record Social(String name, String url, String icon) {
    }

    public record Link(String name, String url, String description, String icon) {
    }
}
