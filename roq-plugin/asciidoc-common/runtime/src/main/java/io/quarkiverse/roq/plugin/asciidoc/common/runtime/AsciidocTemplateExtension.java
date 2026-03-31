package io.quarkiverse.roq.plugin.asciidoc.common.runtime;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.qute.TemplateExtension;

@Singleton
public class AsciidocTemplateExtension {
    @TemplateExtension
    public static Map asciidocAttributes(Page page) {
        if (page.data().containsKey(RoqAsciidocKeys.ASCIIDOC_ATTRIBUTES)) {
            return convertToStringMap(page.data().getJsonObject(RoqAsciidocKeys.ASCIIDOC_ATTRIBUTES).getMap());
        }
        return Map.of();
    }

    public static Map<String, String> convertToStringMap(Map<?, ?> a) {
        Map<String, String> map = new HashMap<>();
        a.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return map;
    }
}
