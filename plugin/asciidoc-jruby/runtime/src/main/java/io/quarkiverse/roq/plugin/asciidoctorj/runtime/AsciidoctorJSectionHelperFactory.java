package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.RawString;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;
import io.quarkus.qute.TemplateExtension;

@EngineConfiguration
public class AsciidoctorJSectionHelperFactory
        implements SectionHelperFactory<AsciidoctorJSectionHelperFactory.AsciidocSectionHelper> {

    private static final LazyValue<AsciidoctorJConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(AsciidoctorJConverter.class).get());

    private final AsciidoctorJConverter converter;

    public AsciidoctorJSectionHelperFactory() {
        // This constructor is only used during build
        // where the converter is not used at all
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
    public AsciidocSectionHelper initialize(SectionInitContext context) {
        return new AsciidocSectionHelper(converter);
    }

    @TemplateExtension(matchNames = { "asciidocify", "asciidocToHtml" })
    static RawString convertToAsciidoc(String text, String ignoredName) {
        return new RawString(CONVERTER.get().apply(text));
    }

    public static class AsciidocSectionHelper implements SectionHelper {

        private final AsciidoctorJConverter converter;

        public AsciidocSectionHelper(AsciidoctorJConverter converter) {
            this.converter = converter;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.execute().thenCompose(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                return CompletedStage.of(new SingleResultNode(converter.apply(sb.toString())));
            });
        }
    }
}
