package io.quarkiverse.roq.plugin.asciidoc.common.runtime;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.qute.TemplateExtension;

@Singleton
public class AsciidocTemplateExtension {
    public static final String FM_ASCIIDOC_ATTRIBUTES = "asciidoc-attributes";

    @TemplateExtension
    public static Map asciidocAttributes(Page page) {
        if (page.data().containsKey(FM_ASCIIDOC_ATTRIBUTES)) {
            return convertToStringMap(page.data().getJsonObject(FM_ASCIIDOC_ATTRIBUTES).getMap());
        }
        return Map.of();
    }

    public static Map<String, String> convertToStringMap(Map<?, ?> a) {
        Map<String, String> map = new HashMap<>();
        a.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return map;
    }
}
