package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqQuteEngineObserver.TEMPLATE_PATH;

import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;

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
        return (SectionHelper.SectionResolutionContext context) -> {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                return CompletedStage.of(
                        new SingleResultNode(
                                converter.apply((Path) context.resolutionContext().getAttribute(TEMPLATE_PATH),
                                        sb.toString())));
            });
        };
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text, String ignoredName, @TemplateAttribute(TEMPLATE_PATH) Object templatePath) {
        return new RawString(CONVERTER.get().apply((Path) templatePath, text));
    }

    public interface AsciidocSectionHelper extends SectionHelper {

    }
}
