package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.*;
import static io.quarkiverse.roq.plugin.asciidoc.common.runtime.AsciidocTemplateExtension.convertToStringMap;

import java.util.List;
import java.util.Map;
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
    private static final String ASCIIDOC_ATTRIBUTES = "attributes";
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
    public ParametersInfo getParameters() {
        return ParametersInfo.builder().addParameter(Parameter.builder(ASCIIDOC_ATTRIBUTES).optional().build())
                .checkNumberOfParams(false).build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        if (block.hasParameter(ASCIIDOC_ATTRIBUTES)) {
            block.addExpression(ASCIIDOC_ATTRIBUTES, block.getParameter(ASCIIDOC_ATTRIBUTES));
        }
        return outerScope;
    }

    @Override
    public AsciidocSectionHelper initialize(SectionInitContext c) {
        final Expression asciidocAttributesExpr = c.getExpression(ASCIIDOC_ATTRIBUTES);
        return new AsciidocSectionHelper(asciidocAttributesExpr);
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text,
            String ignoredName,
            @TemplateAttribute(SOURCE_ROOT_PATH) Object sourceRootPath,
            @TemplateAttribute(SOURCE_PATH) Object templatePath,
            @TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateAttribute(PAGE_URL) Object pageUrl) {
        return convertToAsciidoc(text, ignoredName, Map.of(), sourceRootPath, templatePath, sitePath, siteUrl, pagePath,
                pageUrl);
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text,
            String ignoredName,
            Map<String, Object> attributes,
            @TemplateAttribute(SOURCE_ROOT_PATH) Object sourceRootPath,
            @TemplateAttribute(SOURCE_PATH) Object sourcePath,
            @TemplateAttribute(SITE_PATH) Object sitePath,
            @TemplateAttribute(SITE_URL) Object siteUrl,
            @TemplateAttribute(PAGE_PATH) Object pagePath,
            @TemplateAttribute(PAGE_URL) Object pageUrl) {
        return new RawString(
                CONVERTER.get().apply(text, convertToStringMap(attributes),
                        new RoqTemplateAttributes((String) sourceRootPath, (String) sourcePath, (String) siteUrl,
                                (String) sitePath, (String) pageUrl, (String) pagePath)));
    }

    class AsciidocSectionHelper implements SectionHelper {

        private final Expression asciidocAttributesExpr;

        public AsciidocSectionHelper(Expression asciidocAttributesExpr) {
            this.asciidocAttributesExpr = asciidocAttributesExpr;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            if (asciidocAttributesExpr != null) {
                return context.evaluate(asciidocAttributesExpr)
                        .thenCompose(a -> execute(a, context));
            }
            return execute(null, context);
        }

        private CompletionStage<ResultNode> execute(Object a, SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                Map<String, String> asciidocAttributes = Map.of();
                if (a instanceof Map) {
                    asciidocAttributes = convertToStringMap((Map<?, ?>) a);
                }
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                final ResolutionContext resolutionContext = context.resolutionContext();
                final RoqTemplateAttributes attributes = new RoqTemplateAttributes(
                        (String) resolutionContext.getAttribute(SOURCE_ROOT_PATH),
                        (String) resolutionContext.getAttribute(SOURCE_PATH),
                        (String) resolutionContext.getAttribute(SITE_URL),
                        (String) resolutionContext.getAttribute(SITE_PATH),
                        (String) resolutionContext.getAttribute(PAGE_URL),
                        (String) resolutionContext.getAttribute(PAGE_PATH));
                return CompletedStage.of(
                        new SingleResultNode(
                                converter.apply(sb.toString(), asciidocAttributes, attributes)));
            });
        }
    }

}
