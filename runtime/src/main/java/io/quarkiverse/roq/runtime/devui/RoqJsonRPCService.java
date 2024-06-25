package io.quarkiverse.roq.runtime.devui;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.roq.runtime.StaticPage;
import io.quarkiverse.roq.runtime.StaticPages;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoqJsonRPCService {

    @All
    @Inject
    List<StaticPages> staticPages;

    @Inject
    io.quarkiverse.roq.runtime.RoqGenerator generator;

    @Blocking
    public List<StaticPage> getStaticPages() {
        return StaticPages.merge(staticPages);
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
