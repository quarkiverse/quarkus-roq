package io.quarkiverse.roq.generator.runtime.devui;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkiverse.roq.generator.runtime.RoqGeneratorConfig;
import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoqGeneratorJsonRPCService {

    @All
    @Inject
    List<RoqSelection> selection;

    @Inject
    RoqGenerator generator;

    @Inject
    RoqGeneratorConfig config;

    @Blocking
    public List<SelectedPath> getSelection() {
        return RoqSelection.prepare(config, selection);
    }

    @Blocking
    public int getCount() {
        return getSelection().size();
    }

    public Uni<String> generate() {
        return generator.generate().map(a -> generator.outputDir());
    }

}