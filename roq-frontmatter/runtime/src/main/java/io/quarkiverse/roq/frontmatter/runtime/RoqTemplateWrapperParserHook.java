package io.quarkiverse.roq.frontmatter.runtime;

import jakarta.inject.Inject;

import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.ParserHelper;
import io.quarkus.qute.ParserHook;

@EngineConfiguration
public class RoqTemplateWrapperParserHook implements ParserHook {

    private final RoqTemplateParserFilters filters;

    @Inject
    public RoqTemplateWrapperParserHook(RoqTemplateParserFilters filters) {
        this.filters = filters;
    }

    public RoqTemplateWrapperParserHook() {
        // This constructor is only used during build
        // where the converter is not used at all
        this.filters = null;
    }

    @Override
    public void beforeParsing(ParserHelper parserHelper) {
        if (filters == null) {
            return;
        }
        final WrapperFilter filter = filters.getFilter(parserHelper.getTemplateId());
        if (filter != null) {
            parserHelper.addContentFilter(filter);
        }
    }
}
