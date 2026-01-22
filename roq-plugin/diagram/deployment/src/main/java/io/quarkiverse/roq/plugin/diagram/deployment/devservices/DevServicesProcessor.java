package io.quarkiverse.roq.plugin.diagram.deployment.devservices;

import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import io.quarkiverse.roq.plugin.diagram.deployment.RoqPluginDiagramProcessor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.configuration.ConfigUtils;

public class DevServicesProcessor {

    /**
     * Label to add to shared Dev Service for kroki running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "kroki-dev-service";
    static final String DEV_SERVICE_NAME = "kroki";
    static final int KROKI_PORT = 8000;
    private static final Logger LOGGER = Logger.getLogger(DevServicesProcessor.class);
    private static final String KROKI_URL_KEY = "quarkus.rest-client.kroki-api.url";
    private static final ContainerLocator krokiContainerLocator = new ContainerLocator(DEV_SERVICE_LABEL, KROKI_PORT);
    static volatile DevServicesResultBuildItem.RunningDevService devService;

    static volatile boolean first = true;

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = DevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem startEnvProviderDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            DevServiceConfig krokiDevserviceConfig,
            DevServicesConfig devServicesConfig) {

        if (devServicesDisabled(dockerStatusBuildItem, devServicesConfig, krokiDevserviceConfig)) {
            return null;
        }

        final Optional<ContainerAddress> maybeContainerAddress = krokiContainerLocator.locateContainer(DEV_SERVICE_LABEL,
                true,
                launchMode.getLaunchMode());

        return maybeContainerAddress.map(containerAddress -> DevServicesResultBuildItem.discovered()
                .name(DEV_SERVICE_NAME)
                .containerId(containerAddress.getId())
                .config(Map.of(KROKI_URL_KEY,
                        "http://%s:%d".formatted(containerAddress.getHost(), containerAddress.getPort())))
                .build())
                .orElseGet(() -> DevServicesResultBuildItem.owned()
                        .feature(RoqPluginDiagramProcessor.FEATURE)
                        .serviceName(DEV_SERVICE_NAME)
                        .serviceConfig(krokiDevserviceConfig)
                        .startable(() -> startKroki(devServicesConfig, krokiDevserviceConfig))
                        .postStartHook(DevServicesProcessor::logStarted)
                        .configProvider(Map.of(KROKI_URL_KEY, KrokiContainer::getConnectionInfo))
                        .build());
    }

    private static void logStarted(KrokiContainer container) {
        LOGGER.infof("Kroki dev service started on %s", container.getConnectionInfo());
        LOGGER.infof("Other Quarkus applications in dev mode will find the "
                + "instance automatically. For Quarkus applications in production mode, you can connect to"
                + " this by starting your application with -D%s=%s",
                KROKI_URL_KEY, container.getConnectionInfo());
    }

    private KrokiContainer startKroki(
            DevServicesConfig devServicesConfig, DevServiceConfig krokiDevserviceConfig) {

        KrokiContainer container = new KrokiContainer(DockerImageName.parse(krokiDevserviceConfig.imageName()));

        container.withLabel(DEV_SERVICE_LABEL, DEV_SERVICE_LABEL);
        container.addEnv("KROKI_SAFE_MODE", "UNSAFE");

        devServicesConfig.timeout().ifPresent(container::withStartupTimeout);
        return container;
    }

    private static boolean devServicesDisabled(DockerStatusBuildItem dockerStatusBuildItem, DevServicesConfig devServicesConfig,
            DevServiceConfig krokiDevserviceConfig) {
        if (!(devServicesConfig.enabled() && krokiDevserviceConfig.enabled())) {
            // explicitly disabled
            LOGGER.debug("Not starting dev services for Kroki, as it has been disabled in the config.");
            return true;
        }

        // Check if quarkus.kroki.url is set
        if (ConfigUtils.isPropertyPresent(KROKI_URL_KEY)) {
            LOGGER.debugf("Not starting dev services for Kroki, the %s is configured.", KROKI_URL_KEY);
            return true;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            LOGGER.warnf("Docker isn't working, please configure the Kroki Url property (%s).", KROKI_URL_KEY);
            return true;
        }
        return false;
    }

    /**
     * Container configuring and starting the kroki server.
     */
    private static final class KrokiContainer extends GenericContainer<KrokiContainer> implements Startable {

        private KrokiContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
            withNetwork(Network.SHARED);
            withExposedPorts(KROKI_PORT);
        }

        public int getPort() {
            return getMappedPort(KROKI_PORT);
        }

        @Override
        public String getConnectionInfo() {
            return "http://%s:%s".formatted(getHost(), getPort());
        }

        @Override
        public void close() {
            super.close();
        }
    }
}
