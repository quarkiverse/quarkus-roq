package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Map;

public record TemplateAttributesMapping(Map<String, Attribute> attributes) {
    private record Attribute(String name, String value) {
    }
}
