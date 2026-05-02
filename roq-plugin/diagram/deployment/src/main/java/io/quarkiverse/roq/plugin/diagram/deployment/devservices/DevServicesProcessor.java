package io.quarkiverse.roq.plugin.diagram.deployment.devservices;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.configuration.ConfigUtils;

public class DevServicesProcessor {

    /**
     * Label to add to shared Dev Service for kroki running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "kroki-dev-service";
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
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            DevServiceConfig krokiDevserviceConfig,
            DevServicesConfig devServicesConfig) {

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Kroki devservice starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);
        try {
            devService = startKroki(dockerStatusBuildItem, launchMode, devServicesConfig, krokiDevserviceConfig);
            if (devService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownServer();
                }
                first = true;
                devService = null;
            };
            closeBuildItem.addCloseTask(closeTask, true);
        }

        if (devService.isOwner()) {
            LOGGER.infof("Kroki sev service started on %s", getAcmeEnvUrl());
            LOGGER.infof("Other Quarkus applications in dev mode will find the "
                    + "instance automatically. For Quarkus applications in production mode, you can connect to"
                    + " this by starting your application with -D%s=%s",
                    KROKI_URL_KEY, getAcmeEnvUrl());
        }

        return devService.toBuildItem();
    }

    private Object getAcmeEnvUrl() {
        return devService.getConfig().get(KROKI_URL_KEY);
    }

    private void shutdownServer() {
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                LOGGER.error("Failed to stop the Minio server", e);
            } finally {
                devService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startKroki(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            DevServicesConfig devServicesConfig, DevServiceConfig krokiDevserviceConfig) {
        if (!(devServicesConfig.enabled() && krokiDevserviceConfig.enabled())) {
            // explicitly disabled
            LOGGER.debug("Not starting dev services for Kroki, as it has been disabled in the config.");
            return null;
        }

        // Check if quarkus.kroki.url is set
        if (ConfigUtils.isPropertyPresent(KROKI_URL_KEY)) {
            LOGGER.debugf("Not starting dev services for Kroki, the % is configured.", KROKI_URL_KEY);
            return null;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            LOGGER.warnf("Docker isn't working, please configure the Kroki Url property (%s).", KROKI_URL_KEY);
            return null;
        }

        final Optional<ContainerAddress> maybeContainerAddress = krokiContainerLocator.locateContainer(DEV_SERVICE_LABEL,
                true,
                launchMode.getLaunchMode());

        // Starting the server
        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultAcmeEnvBrokerSupplier = () -> {
            KrokiContainer container = new KrokiContainer(
                    DockerImageName.parse(krokiDevserviceConfig.imageName()));

            container.withLabel(DEV_SERVICE_LABEL, DEV_SERVICE_LABEL);
            container.addEnv("KROKI_SAFE_MODE", "UNSAFE");

            devServicesConfig.timeout().ifPresent(container::withStartupTimeout);

            container.start();
            return new DevServicesResultBuildItem.RunningDevService(DEV_SERVICE_LABEL,
                    container.getContainerId(),
                    container::close,
                    Map.of(KROKI_URL_KEY, "http://%s:%d".formatted(container.getHost(), container.getPort())));
        };

        return maybeContainerAddress
                .map(containerAddress -> new DevServicesResultBuildItem.RunningDevService(DEV_SERVICE_LABEL,
                        containerAddress.getId(),
                        null,
                        Map.of(KROKI_URL_KEY,
                                "http://%s:%d".formatted(containerAddress.getHost(), containerAddress.getPort()))))
                .orElseGet(defaultAcmeEnvBrokerSupplier);
    }

    /**
     * Container configuring and starting the kroki server.
     */
    private static final class KrokiContainer extends GenericContainer<KrokiContainer> {

        private KrokiContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
            withNetwork(Network.SHARED);
            withExposedPorts(KROKI_PORT);
        }

        public int getPort() {
            return getMappedPort(KROKI_PORT);
        }
    }
}
