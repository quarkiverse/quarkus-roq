package io.quarkiverse.roq.frontmatter.deployment.apptest;

import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "incidents", type = DataMapping.Type.ARRAY_DIR)
public record Incidents(List<Incident> incidents) {

    public record Incident(String id, String name, String description) {
    }
}
