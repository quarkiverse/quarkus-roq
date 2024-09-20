package io.quarkiverse.roq.frontmatter.runtime;

import java.time.LocalDateTime;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class RoqTemplateGlobal {
    public static LocalDateTime now = LocalDateTime.now();
}
