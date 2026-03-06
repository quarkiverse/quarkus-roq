package io.quarkiverse.roq.editor.deployment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.editor.deployment.git.GitSyncService;
import io.quarkiverse.roq.editor.deployment.git.GitSyncServiceImpl;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorJsonRPCService;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.console.ConsoleCommand;
import io.quarkus.deployment.console.ConsoleStateManager;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.dev.spi.DevModeType;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.devmode.IdeHelper;

public class RoqEditorProcessor {

    private static final Logger LOG = Logger.getLogger(RoqEditorProcessor.class);
    private static final String FEATURE = "roq-editor";
    static volatile ConsoleStateManager.ConsoleContext context;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    AdditionalBeanBuildItem registerAdditionalBeans() {
        return AdditionalBeanBuildItem.unremovableOf(RoqEditorJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Produce(ServiceStartBuildItem.class)
    void setupConsole(HttpRootPathBuildItem rp,
            NonApplicationRootPathBuildItem np,
            LaunchModeBuildItem launchModeBuildItem) {
        if (launchModeBuildItem.getDevModeType().orElse(null) != DevModeType.LOCAL) {
            return;
        }
        if (context == null) {
            context = ConsoleStateManager.INSTANCE.createContext("Roq");
        }
        Config config = ConfigProvider.getConfig();
        String host = config.getOptionalValue("quarkus.http.host", String.class).orElse("localhost");
        boolean isInsecureDisabled = config.getOptionalValue("quarkus.http.insecure-requests", String.class)
                .map("disabled"::equals)
                .orElse(false);

        String port = isInsecureDisabled
                ? config.getOptionalValue("quarkus.http.ssl-port", String.class).orElse("8443")
                : config.getOptionalValue("quarkus.http.port", String.class).orElse("8080");

        String protocol = isInsecureDisabled ? "https" : "http";
        context.reset(new ConsoleCommand('c', "Open the Roq Editor in a browser", null,
                () -> IdeHelper.openBrowser(rp, np, protocol, "/q/dev-ui/quarkus-roq-editor/roq-editor", host, port)));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem registerJsonRpcService() {
        return new JsonRPCProvidersBuildItem(RoqEditorJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createDevUiCard(RoqEditorConfig editorConfig) {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:pencil")
                .componentLink("qwc-roq-editor.js")
                .title("Roq Editor"));

        // Fallback markups
        List<String> markups = new ArrayList<>();
        markups.add("markdown");
        markups.add("asciidoc");
        markups.add("html");

        card.addBuildTimeData("markups", markups);

        // Manually map to kebab-case to match JavaScript expectations
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("page-markup", editorConfig.pageMarkup().toString());
        configMap.put("doc-markup", editorConfig.docMarkup().toString());

        Map<String, Object> visualEditor = new HashMap<>();
        visualEditor.put("enabled", editorConfig.visualEditor().enabled());
        visualEditor.put("safe", editorConfig.visualEditor().safe());
        configMap.put("visual-editor", visualEditor);

        Map<String, Object> suggestedPath = new HashMap<>();
        suggestedPath.put("enabled", editorConfig.suggestedPath().enabled());
        configMap.put("suggested-path", suggestedPath);

        Map<String, Object> sync = new HashMap<>();
        sync.put("enabled", editorConfig.sync().enabled());

        Map<String, Object> autoSync = new HashMap<>();
        autoSync.put("enabled", editorConfig.sync().autoSync().enabled());
        autoSync.put("interval-seconds", editorConfig.sync().autoSync().intervalSeconds());
        sync.put("auto-sync", autoSync);

        Map<String, Object> autoPublish = new HashMap<>();
        autoPublish.put("enabled", editorConfig.sync().autoPublish().enabled());
        autoPublish.put("interval-seconds", editorConfig.sync().autoPublish().intervalSeconds());
        sync.put("auto-publish", autoPublish);

        Map<String, Object> commitMessage = new HashMap<>();
        commitMessage.put("template", editorConfig.sync().commitMessage().template());
        sync.put("commit-message", commitMessage);

        configMap.put("sync", sync);

        card.addBuildTimeData("config", configMap);

        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    BuildTimeActionBuildItem registerGitActions(RoqEditorConfig editorConfig,
            RoqSiteConfig siteConfig,
            LaunchModeBuildItem launchModeBuildItem,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {

        if (!editorConfig.sync().enabled() || launchModeBuildItem.getDevModeType().orElse(null) != DevModeType.LOCAL) {
            return null;
        }

        // Get the project root directory from the application model
        Path projectRoot = curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getWorkspaceModule().getModuleDir()
                .toPath();
        GitSyncService gitService = new GitSyncServiceImpl(editorConfig, siteConfig, projectRoot.toFile());

        BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();

        actions.actionBuilder().methodName("getSyncStatus").function(parameters -> {
            String passphrase = parseString(parameters.get("passphrase"));
            boolean skipFetch = parseBoolean(parameters.get("skipFetch"));
            return CompletableFuture.supplyAsync(() -> gitService.getStatus(passphrase, skipFetch));
        }).build();

        actions.actionBuilder().methodName("syncContent").function(parameters -> {
            String passphrase = parseString(parameters.get("passphrase"));
            return CompletableFuture.supplyAsync(() -> gitService.sync(passphrase));
        }).build();

        actions.actionBuilder().methodName("publishContent").function(parameters -> {
            String commitMessage = String.valueOf(parameters.get("message"));
            String passphrase = parseString(parameters.get("passphrase"));
            List<String> filePaths = extractList(parameters.get("filePaths"));
            return CompletableFuture.supplyAsync(() -> gitService.publish(commitMessage, passphrase, filePaths));
        }).build();

        actions.actionBuilder().methodName("publishAndSync").function(parameters -> {
            String commitMessage = String.valueOf(parameters.get("message"));
            String passphrase = parseString(parameters.get("passphrase"));
            List<String> filePaths = extractList(parameters.get("filePaths"));
            return CompletableFuture.supplyAsync(() -> gitService.publishAndSync(commitMessage, passphrase, filePaths));
        }).build();

        return actions;
    }

    private String parseString(Object value) {
        if (value == null)
            return null;
        String str = String.valueOf(value);
        return (str.isEmpty() || "null".equals(str)) ? null : str;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean)
            return (boolean) value;
        if (value instanceof String)
            return Boolean.parseBoolean((String) value);
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractList(Object value) {
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }
}
