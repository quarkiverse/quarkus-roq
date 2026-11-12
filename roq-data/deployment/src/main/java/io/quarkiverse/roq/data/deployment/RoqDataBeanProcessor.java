package io.quarkiverse.roq.data.deployment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.JavaTimeSubstitutions;
import io.quarkiverse.roq.data.runtime.RoqDataRecorder;
import io.quarkiverse.roq.deployment.items.LargeRecordBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
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
            BuildProducer<LargeRecordBuildItem> resourceProducer,
            RoqDataRecorder recorder,
            RecorderContext recorderContext) {

        // data mapping beans may hold java.time values, which the recorder can't rebuild on its own
        recorderContext.registerSubstitution(LocalDate.class, String.class, JavaTimeSubstitutions.LocalDates.class);
        recorderContext.registerSubstitution(LocalDateTime.class, String.class, JavaTimeSubstitutions.LocalDateTimes.class);
        recorderContext.registerSubstitution(LocalTime.class, String.class, JavaTimeSubstitutions.LocalTimes.class);
        recorderContext.registerSubstitution(Instant.class, String.class, JavaTimeSubstitutions.Instants.class);
        recorderContext.registerSubstitution(OffsetDateTime.class, String.class, JavaTimeSubstitutions.OffsetDateTimes.class);
        recorderContext.registerSubstitution(ZonedDateTime.class, String.class, JavaTimeSubstitutions.ZonedDateTimes.class);

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
            String resourcePath = "roq-recorded/data-" + roqData.getName() + ".ser";
            resourceProducer
                    .produce(new LargeRecordBuildItem(resourcePath,
                            LargeRecords.serializeJson(roqData.getData())));
            beansProducer.produce(SyntheticBeanBuildItem.configure(cl)
                    .scope(ApplicationScoped.class)
                    .named(roqData.getName())
                    .runtimeValue(recorder.createRoqDataJson(resourcePath))
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
                    .runtimeValue(recorder.createRoqDataBean(beanBuildItem.getData()))
                    .unremovable()
                    .done());
            beans.add("    - %s[name=%s]".formatted(beanBuildItem.getBeanClass().getName(), beanBuildItem.getName()));
        }

        if (!beans.isEmpty() && (config.logDataBeans() || LOG.isDebugEnabled())) {
            final String message = "Roq data beans%s: %n%s".formatted(
                    roqDataJsonBuildItems.isEmpty() ? "" : " (* add a @DataMapping to enable type-safety)",
                    String.join(System.lineSeparator(), beans));
            if (config.logDataBeans()) {
                LOG.info(message);
            } else {
                LOG.debug(message);
            }
        }
    }

}
