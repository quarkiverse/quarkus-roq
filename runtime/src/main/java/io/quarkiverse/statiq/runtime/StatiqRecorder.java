package io.quarkiverse.statiq.runtime;

import java.util.List;
import java.util.Set;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class StatiqRecorder {

    public RuntimeValue<StatiqGeneratorConfig> createGeneratorConfig(List<String> fixedPaths, String outputDir) {
        return new RuntimeValue<>() {
            @Override
            public StatiqGeneratorConfig getValue() {
                return new StatiqGeneratorConfig(fixedPaths, outputDir);
            }
        };
    }

    public void setStatiqPages(Set<String> staticPaths) {
        FixedStaticPagesProvider.setStaticPaths(staticPaths);
    }

    public Handler<RoutingContext> createGenerateHandler() {
        final InstanceHandle<StatiqGenerator> generatorInstanceHandle = Arc.container().instance(StatiqGenerator.class);
        return generatorInstanceHandle.get();
    }
}
