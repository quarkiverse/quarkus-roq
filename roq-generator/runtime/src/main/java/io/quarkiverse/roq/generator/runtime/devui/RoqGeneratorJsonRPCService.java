package io.quarkiverse.roq.generator.runtime.devui;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoqGeneratorJsonRPCService {

    @All
    @Inject
    List<RoqSelection> staticPages;

    @Inject
    RoqGenerator generator;

    @Blocking
    public List<SelectedPath> getStaticPages() {
        return RoqSelection.merge(staticPages);
    }

    @Blocking
    public int getStaticCount() {
        return getStaticPages().size();
    }

    public Uni<String> generate() {
        Map<String, String> config = Map.of("quarkus.http.port", "8081");
        return generator.generate().map(a -> generator.outputDir());
    }

}
