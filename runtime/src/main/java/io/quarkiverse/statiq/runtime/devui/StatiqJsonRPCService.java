package io.quarkiverse.statiq.runtime.devui;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.statiq.runtime.StatiqGenerator;
import io.quarkiverse.statiq.runtime.StatiqPage;
import io.quarkiverse.statiq.runtime.StatiqPages;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class StatiqJsonRPCService {

    @All
    @Inject
    List<StatiqPages> statiqPages;

    @Inject
    StatiqGenerator generator;

    @Blocking
    public List<StatiqPage> getStatiqPages() {
        return StatiqPages.merge(statiqPages);
    }

    @Blocking
    public int getStatiqCount() {
        return getStatiqPages().size();
    }

    public Uni<String> generate() {
        Map<String, String> config = Map.of("quarkus.http.port", "8081");
        return generator.generate().map(a -> generator.outputDir());
    }

}
