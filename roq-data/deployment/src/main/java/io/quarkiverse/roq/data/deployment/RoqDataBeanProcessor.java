package io.quarkiverse.roq.data.deployment;

import java.util.*;

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
        TreeMap<String, JsonObject> beansMap;

        var mapOfFolders = new HashMap<String, TreeMap<String, JsonObject>>();
        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            if (roqData.getName().contains("_")) {
                // Subfolder case
                // Test that there is only one level of subfolder
                long count = roqData.getName().chars().filter(c -> c == '_').count();
                if (count > 1) {
                    throw new IllegalStateException("Unsupported more than one subfolder %s".formatted(roqData.getName()));
                }

                var fileName = roqData.getName();
                var key = fileName.substring(fileName.indexOf("_") + 1);

                var folderName = fileName.substring(0, fileName.lastIndexOf('_'));
                if (mapOfFolders.containsKey(folderName)) {
                    mapOfFolders.get(folderName).put(key, (JsonObject) roqData.getData());
                } else {
                    beansMap = new TreeMap<>();
                    beansMap.put(key, (JsonObject) roqData.getData());
                    mapOfFolders.put(folderName, beansMap);
                }
            } else {
                // Files at the root of data folder
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

        // Register TreeMap beans
        for (Map.Entry<String, TreeMap<String, JsonObject>> entry : mapOfFolders.entrySet()) {
            beansProducer.produce(SyntheticBeanBuildItem.configure(TreeMap.class)
                    .scope(ApplicationScoped.class)
                    .named(entry.getKey())
                    .runtimeValue(recorder.createRoqDataJson(entry.getValue()))
                    .unremovable()
                    .done());
            beans.add("    - %s[name=%s]*".formatted(TreeMap.class.getName(), entry.getKey()));

        }

        if (!beans.isEmpty() && config.logDataBeans()) {
            LOG.infof("Roq data beans%s: %n%s",
                    roqDataJsonBuildItems.isEmpty() ? "" : " (* add a @DataMapping to enable type-safety)",
                    String.join(System.lineSeparator(), beans));
        }
    }

}
