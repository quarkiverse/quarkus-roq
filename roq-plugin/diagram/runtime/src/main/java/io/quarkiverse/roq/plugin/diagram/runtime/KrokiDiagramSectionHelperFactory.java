package io.quarkiverse.roq.plugin.diagram.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.Expression;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.Scope;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;

/**
 * Qute template section helper for generating diagram from text description.
 * Extends QuteBarCode to provide QR code generation functionality using the Okapi library.
 */
@EngineConfiguration
public class KrokiDiagramSectionHelperFactory
        implements SectionHelperFactory<KrokiDiagramSectionHelperFactory.KrokiDiagramSectionHelper> {

    private static final LazyValue<DiagramConverter> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(DiagramConverter.class).get());

    private DiagramConverter converter;

    public KrokiDiagramSectionHelperFactory() {
        this.converter = null;
    }

    @Inject
    public KrokiDiagramSectionHelperFactory(DiagramConverter converter) {
        this.converter = converter;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of("diagram");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder().addParameter("language").build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        TypeUtil.declareBlock(block, "language", "alt", "width", "height", "diagramOutputFormat", "asciidoc");
        return SectionHelperFactory.super.initializeBlock(outerScope, block);
    }

    @Override
    public KrokiDiagramSectionHelper initialize(SectionInitContext context) {
        Map<String, Expression> params = TypeUtil.collectExpressions(context,
                "language",
                "alt",
                "width",
                "height",
                "diagramOutputFormat",
                "asciidoc");
        return new KrokiDiagramSectionHelper(params, converter);
    }

    /**
     * Section helper implementation that handles barcode generation.
     */
    static class KrokiDiagramSectionHelper implements SectionHelper {

        private Map<String, Expression> params;
        private DiagramConverter encoder;

        public KrokiDiagramSectionHelper(Map<String, Expression> params, DiagramConverter encoder) {
            this.params = params;
            this.encoder = encoder;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return context.evaluate(params)
                    .thenApply(this::extractParams)
                    .thenCombine(
                            extractDiagramBody(context),
                            (diagramParams, body) -> new SingleResultNode(
                                    encoder.encode(body, diagramParams)));

        }

        private CompletionStage<String> extractDiagramBody(SectionResolutionContext context) {
            return context.execute().thenApply(rn -> {
                StringBuilder sb = new StringBuilder();
                rn.process(sb::append);
                var value = sb.toString();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("No diagram body found");
                }
                return value;
            });
        }

        private DiagramParams extractParams(Map<String, Object> values) {
            return new DiagramParams.Builder()
                    .setLanguage(values.get("language"))
                    .setAlt(values.get("alt"))
                    .setWidth(values.get("width"))
                    .setHeight(values.get("height"))
                    .setDiagramOutputFormat(values.get("diagramOutputFormat"))
                    .setAsciidoc(values.get("asciidoc"))
                    .build();
        }
    }
}
