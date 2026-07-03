package io.quarkiverse.roq.theme.linktree;

import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("profile")
public record Profile(String name, String handle, String title, String bio,
        String image, String tree, List<Social> social) {

    public record Social(String name, String url, String icon) {
    }
}
