package io.quarkiverse.roq.frontmatter.deployment.items.assemble;

import java.nio.file.Path;

public record RoqFrontMatterAttachment(String name, Path path, byte[] content) {

    public RoqFrontMatterAttachment(String name, Path path) {
        this(name, path, null);
    }

    public RoqFrontMatterAttachment(String name, byte[] content) {
        this(name, null, content);
    }

    public boolean isFile() {
        return path != null;
    }
}
