package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.*;

import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.*;
import io.quarkus.qute.TemplateExtension.TemplateAttribute;

@EngineConfiguration
public class AsciidoctorJSectionHelperFactory
        implements SectionHelperFactory<AsciidoctorJSectionHelperFactory.AsciidocSectionHelper> {

    private static final LazyValue<AsciidoctorJConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(AsciidoctorJConverter.class).get());
    private final AsciidoctorJConverter converter;

    public AsciidoctorJSectionHelperFactory() {
        this.converter = null;
    }

    @Inject
    public AsciidoctorJSectionHelperFactory(AsciidoctorJConverter converter) {
        this.converter = converter;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("asciidoc", "ascii");
    }

    @Override
    public AsciidocSectionHelper initialize(SectionInitContext c) {
        return new AsciidocSectionHelper();
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text,
            String ignoredName,
            @TemplateAttribute(SOURCE_PATH) Object templatePath,
            @TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateAttribute(PAGE_URL) Object pageUrl) {
        return new RawString(CONVERTER.get().apply(text, new RoqTemplateAttributes((String) templatePath, (String) siteUrl,
                (String) sitePath, (String) pageUrl, (String) pagePath)));
    }

    class AsciidocSectionHelper implements SectionHelper {

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                final ResolutionContext resolutionContext = context.resolutionContext();
                final RoqTemplateAttributes attributes = new RoqTemplateAttributes(
                        (String) resolutionContext.getAttribute(SOURCE_PATH),
                        (String) resolutionContext.getAttribute(SITE_URL),
                        (String) resolutionContext.getAttribute(SITE_PATH),
                        (String) resolutionContext.getAttribute(PAGE_URL),
                        (String) resolutionContext.getAttribute(PAGE_PATH));
                return CompletedStage.of(
                        new SingleResultNode(
                                converter.apply(sb.toString(), attributes)));
            });
        }
    }
}
