package io.quarkiverse.roq.frontmatter.runtime;

import java.util.Map;

public record RoqTemplateParserFilters(Map<String, WrapperFilter> filters) {
    public WrapperFilter getFilter(String templateId) {
        return filters.get(templateId);
    }

}
