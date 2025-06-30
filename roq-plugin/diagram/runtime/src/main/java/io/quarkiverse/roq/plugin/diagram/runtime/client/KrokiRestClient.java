package io.quarkiverse.roq.plugin.diagram.runtime.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "kroki-api")
public interface KrokiRestClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    byte[] generateDiagram(DiagramRequest request);

    record DiagramRequest(String diagramType, String diagramSource, String outputFormat) {
    }
}
