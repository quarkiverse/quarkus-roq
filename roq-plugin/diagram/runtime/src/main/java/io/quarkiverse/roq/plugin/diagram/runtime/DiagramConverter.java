package io.quarkiverse.roq.plugin.diagram.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.plugin.diagram.runtime.client.KrokiRestClient;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class DiagramConverter {

    private final KrokiRestClient krokiApi;
    private final Logger logger;

    public DiagramConverter(@RestClient KrokiRestClient krokiApi, Logger logger) {
        this.krokiApi = krokiApi;
        this.logger = logger;
    }

    public enum DiagramOutputFormat {
        png,
        svg;

        public String getFormatString() {
            return this.name().toLowerCase();
        }

        public String getMimeType() {
            return switch (this) {
                case png -> "image/png";
                case svg -> "image/svg+xml";
            };
        }
    }

    /**
     * Encodes the given diagram source into an <code><img></code> html tag or the equivalent asciidoctor macro with the encoded
     * diagram image.
     *
     * Leverages the data inlining capability to produce images with the following template :
     * <code>data:<mime_type>;base64,<encoded_image></code>
     *
     * This allows to work around the hassle of file writing and image linking.
     *
     * @param diagramSource The diagram as text
     * @return An <code><img></code> html tag or the equivalent asciidoctor macro with the encoded diagram image.
     */
    public String encode(String diagramSource, DiagramParams params) {

        byte[] imageBytes;
        String dataUri = null;
        try {
            imageBytes = krokiApi.generateDiagram(
                    new KrokiRestClient.DiagramRequest(params.language(), diagramSource,
                            params.diagramOutputFormat().getFormatString()));
            dataUri = dataUri(imageBytes, params.diagramOutputFormat());
        } catch (Exception e) {
            logger.error("Failed to generate diagram", e);
            String errorSvg = """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 350" width="600" height="350">
                      <rect width="600" height="350" fill="#ebcfb2FF"></rect>
                      <text x="50%" y="33%" dominant-baseline="middle" text-anchor="middle" font-family="monospace" font-size="30px" fill="#B38D97FF">Oh, oh...</text>
                      <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="monospace" font-size="30px" fill="#B38D97FF">It seems there was an error...</text>
                    </svg>
                    """;
            dataUri = dataUri(errorSvg.getBytes(StandardCharsets.UTF_8), DiagramOutputFormat.svg);
        }

        if (params.asciidoc()) {
            return "image::%s[alt=\"%s\", width=%d,height=%d]".formatted(
                    dataUri,
                    params.alt(),
                    params.width(),
                    params.height());
        }

        return String.format(
                "<img src=\"%s\" alt=\"%s\" width=\"%d\" height=\"%d\"/>",
                dataUri, params.alt(), params.width(), params.height());
    }

    private String dataUri(byte[] bytes, DiagramOutputFormat outputFormat) {
        return "data:%s;base64,%s".formatted(outputFormat.getMimeType(), Base64.getEncoder().encodeToString(bytes));
    }
}
