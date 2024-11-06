package io.quarkiverse.roq.data.deployment;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.RoqDataRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoqDataBeanProcessor {

    private static final String FEATURE = "roq-data";
    private static final Logger LOG = Logger.getLogger(RoqDataBeanProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateSyntheticBeans(RoqDataConfig config,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            List<RoqDataBeanBuildItem> dataBeanBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            RoqDataRecorder recorder) {
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(JsonObject.class).serialization().constructors().fields().methods().build());

        List<String> beans = new ArrayList<>(roqDataJsonBuildItems.size());

        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            final Class<?> cl;
            if (roqData.getData() instanceof JsonObject) {
                cl = JsonObject.class;
            } else if (roqData.getData() instanceof JsonArray) {
                cl = JsonArray.class;
            } else {
                throw new IllegalStateException("Unsupported Json data bean type for %s".formatted(roqData.getName()));
            }
            beansProducer.produce(SyntheticBeanBuildItem.configure(cl)
                    .scope(ApplicationScoped.class)
                    .named(roqData.getName())
                    .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                    .unremovable()
                    .done());
            beans.add("    - %s[name=%s]*".formatted(cl.getName(), roqData.getName()));
        }
        for (RoqDataBeanBuildItem beanBuildItem : dataBeanBuildItems) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(beanBuildItem.getBeanClass()).serialization()
                    .constructors().fields().methods().build());
            beansProducer.produce(SyntheticBeanBuildItem.configure(beanBuildItem.getBeanClass())
                    .scope(beanBuildItem.isRecord() ? Singleton.class : ApplicationScoped.class)
                    .named(beanBuildItem.getName())
                    .runtimeValue(recorder.createRoqDataJson(beanBuildItem.getData()))
                    .unremovable()
                    .done());
            beans.add("    - %s[name=%s]".formatted(beanBuildItem.getBeanClass().getName(), beanBuildItem.getName()));
        }
        if (!beans.isEmpty() && config.logDataBeans()) {
            LOG.infof("Roq data beans%s: %n%s",
                    roqDataJsonBuildItems.isEmpty() ? "" : " (* use @DataMapping to enable type-safety)",
                    String.join(System.lineSeparator(), beans));
        }
    }

}
