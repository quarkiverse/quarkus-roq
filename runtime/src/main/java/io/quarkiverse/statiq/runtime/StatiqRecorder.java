package io.quarkiverse.statiq.runtime;

import java.util.List;

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

    public Handler<RoutingContext> createGenerateHandler(List<String> staticPaths) {
        final InstanceHandle<StatiqGenerator> generatorInstanceHandle = Arc.container().instance(StatiqGenerator.class);
        final StatiqGenerator statiqGenerator = generatorInstanceHandle.get();
        statiqGenerator.addFixedStaticPages(staticPaths);
        return statiqGenerator;
    }
}
