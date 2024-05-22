package io.quarkiverse.statiq.runtime.devui;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.statiq.runtime.StatiqGenerator;
import io.quarkiverse.statiq.runtime.StatiqPage;
import io.quarkiverse.statiq.runtime.StatiqPages;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.NonBlocking;

@ApplicationScoped
public class StatiqJsonRPCService {

    @All
    @Inject
    List<StatiqPages> statiqPages;

    @Inject
    StatiqGenerator generator;

    @NonBlocking
    public List<StatiqPage> getPages() {
        return StatiqPages.merge(statiqPages);
    }

}
