package io.quarkiverse.roq.plugin.qrcode.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import io.quarkus.qute.Expression;
import io.quarkus.qute.Parameter;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.Scope;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.SingleResultNode;

/**
 * Abstract base class for barcode generation section helpers in Qute templates.
 * Provides common functionality for handling barcode parameters and encoding.
 */
public abstract class QuteBarCode implements SectionHelperFactory<QuteBarCode.CustomSectionHelper> {

    /**
     * Functional interface for barcode encoding implementations.
     */
    @FunctionalInterface
    public static interface BarCodeEncoder {
        /**
         * Encodes a barcode with the given parameters.
         *
         * @param value The content to encode in the barcode
         * @param alt The alt text for accessibility
         * @param foreground The foreground color
         * @param background The background color
         * @param width The width in pixels
         * @param height The height in pixels
         * @param asciidoc Whether to render the barcode as an Asciidoc image
         * @return The encoded barcode as a String (typically HTML)
         */
        String encode(String value, String alt, String foreground, String background, int width, int height, Boolean asciidoc);
    }

    private String name;
    private BarCodeEncoder encoder;

    /**
     * Creates a new barcode section helper.
     *
     * @param name The name of the section helper in templates
     * @param encoder The encoder implementation to use
     */
    public QuteBarCode(String name, BarCodeEncoder encoder) {
        this.name = name;
        this.encoder = encoder;
    }

    @Override
    public List<String> getDefaultAliases() {
        return List.of(name);
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder()
                .addParameter(Parameter.builder("value"))
                .addParameter(Parameter.builder("alt").optional())
                .addParameter(Parameter.builder("foreground").optional())
                .addParameter(Parameter.builder("background").optional())
                .addParameter(Parameter.builder("width").optional())
                .addParameter(Parameter.builder("height").optional())
                .addParameter(Parameter.builder("asciidoc").optional())
                .build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        TypeUtil.declareBlock(block, "value", "alt", "foreground", "background", "width", "height", "asciidoc");
        return SectionHelperFactory.super.initializeBlock(outerScope, block);
    }

    @Override
    public CustomSectionHelper initialize(SectionInitContext context) {
        TypeUtil.requireParameter(context, "value");
        Map<String, Expression> params = TypeUtil.collectExpressions(context,
                "value",
                "alt",
                "foreground",
                "background",
                "width",
                "height",
                "asciidoc");
        return new CustomSectionHelper(params, encoder);
    }

    /**
     * Section helper implementation that handles barcode generation.
     */
    static class CustomSectionHelper implements SectionHelper {

        private Map<String, Expression> params;
        private BarCodeEncoder encoder;

        public CustomSectionHelper(Map<String, Expression> params, BarCodeEncoder encoder) {
            this.params = params;
            this.encoder = encoder;
        }

        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return Futures.evaluateParams(params, context.resolutionContext())
                    .thenApply(values -> {
                        String value = TypeUtil.typecheckValue(values, "value", String.class);
                        String alt = TypeUtil.typecheckValue(values, "alt", String.class, "QR Code");
                        String foreground = TypeUtil.typecheckValue(values, "foreground", String.class, "black");
                        String background = TypeUtil.typecheckValue(values, "background", String.class, "white");
                        Integer width = TypeUtil.typecheckValue(values, "width", Integer.class, 200);
                        Integer height = TypeUtil.typecheckValue(values, "height", Integer.class, 200);
                        Boolean asciidoc = TypeUtil.typecheckValue(values, "asciidoc", Boolean.class, false);
                        return new SingleResultNode(
                                encoder.encode(value, alt, foreground, background, width, height, asciidoc));
                    });
        }
    }

}
