package io.quarkiverse.roq.editor.deployment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.roq.editor.deployment.content.RoqEditorContentService;
import io.quarkiverse.roq.editor.deployment.git.GitSyncService;
import io.quarkiverse.roq.editor.deployment.git.GitSyncServiceImpl;
import io.quarkiverse.roq.editor.deployment.git.RoqEditorGitBuildActions;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorImageResource;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorJsonRPCService;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.SuppressNonRuntimeConfigChangedWarningBuildItem;
import io.quarkus.deployment.console.ConsoleCommand;
import io.quarkus.deployment.console.ConsoleStateManager;
import io.quarkus.deployment.logging.LogCleanupFilterBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.dev.console.DevConsoleManager;
import io.quarkus.dev.spi.DevModeType;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.devmode.IdeHelper;

public class RoqEditorProcessor {

    private static final String FEATURE = "roq-editor";
    static volatile ConsoleStateManager.ConsoleContext context;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // Workaround for SmallRye Config greedy wildcard bug (smallrye/smallrye-config#1492).
    // TODO: remove when Quarkus upgrades to SmallRye Config 3.17+
    @BuildStep
    void suppressWildcardMapMismatch(BuildProducer<SuppressNonRuntimeConfigChangedWarningBuildItem> producer) {
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("editor.collections.*"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("editor.collections.*.name"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("editor.collections.*.sync-name"));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    List<LogCleanupFilterBuildItem> cleanupLoudJGitLogs() {
        return List.of(
                new LogCleanupFilterBuildItem("org.eclipse.jgit.internal.transport.sshd.CachingKeyPairProvider",
                        "Mismatched private key check values"),
                new LogCleanupFilterBuildItem("org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser",
                        "readPrivateKeys"),
                new LogCleanupFilterBuildItem("org.apache.sshd.common.config.keys.FilePasswordProvider",
                        "decode"));
    }

    @Produce(ServiceStartBuildItem.class)
    @BuildStep(onlyIf = IsDevelopment.class)
    void setupConsole(HttpRootPathBuildItem rp, NonApplicationRootPathBuildItem np, LaunchModeBuildItem launchModeBuildItem) {
        if (launchModeBuildItem.getDevModeType().orElse(null) != DevModeType.LOCAL) {
            return;
        }
        if (context == null) {
            context = ConsoleStateManager.INSTANCE.createContext("Roq");
        }
        Config c = ConfigProvider.getConfig();
        String host = c.getOptionalValue("quarkus.http.host", String.class).orElse("localhost");
        boolean isInsecureDisabled = c.getOptionalValue("quarkus.http.insecure-requests", String.class)
                .map("disabled"::equals)
                .orElse(false);

        String port = isInsecureDisabled
                ? c.getOptionalValue("quarkus.http.ssl-port", String.class).orElse("8443")
                : c.getOptionalValue("quarkus.http.port", String.class).orElse("8080");

        String protocol = isInsecureDisabled ? "https" : "http";
        context.reset(new ConsoleCommand('m', "Open the Roq Editor in a browser (Manage content)", null,
                () -> IdeHelper.openBrowser(rp, np, protocol, "/q/dev-ui/quarkus-roq-editor/roq-editor", host, port)));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRPCServiceForCache() {
        return new JsonRPCProvidersBuildItem(RoqEditorJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(
            RoqEditorConfig config,
            CurateOutcomeBuildItem bi,
            Capabilities capabilities,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList) {
        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Roq Editor")
                .componentLink("qwc-roq-editor.js")
                .icon("font-awesome-solid:pencil"));
        final boolean assistantIsAvailable = capabilities.isPresent(Capability.ASSISTANT);
        List<String> markups = new ArrayList<>(markupList.stream().map(RoqFrontMatterQuteMarkupBuildItem::name).toList());
        markups.add("html");
        pageBuildItem.addBuildTimeData("markups", markups);
        pageBuildItem.addBuildTimeData("config", config);
        pageBuildItem.addBuildTimeData("assistantIsAvailable", assistantIsAvailable);
        return pageBuildItem;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    AdditionalBeanBuildItem registerImageResource() {
        return AdditionalBeanBuildItem.unremovableOf(RoqEditorImageResource.class);
    }

    // Static so the instance (and its AtomicReference status) survives hot-reloads.
    private static volatile RoqEditorContentService contentService;

    // writeStatus is a build-time action (not runtime) so it remains available during hot-reload
    // when the Arc container is destroyed and runtime JSON-RPC methods are unavailable.
    @BuildStep(onlyIf = IsDevelopment.class)
    BuildTimeActionBuildItem registerWriteActions(ProjectRootBuildItem projectRoot) {
        if (contentService == null) {
            contentService = new RoqEditorContentService(projectRoot.path());
        }
        DevConsoleManager.register("roq-submit-write", params -> {
            contentService.submitWrite(Path.of(params.get("path")), params.get("content"));
            return null;
        });
        DevConsoleManager.register("roq-submit-write-and-rename", params -> {
            contentService.submitWriteAndRename(Path.of(params.get("writePath")), params.get("content"),
                    Path.of(params.get("from")), Path.of(params.get("to")));
            return null;
        });
        DevConsoleManager.register("roq-submit-rename", params -> {
            contentService.submitRename(Path.of(params.get("from")), Path.of(params.get("to")));
            return null;
        });
        DevConsoleManager.register("roq-submit-create", params -> {
            contentService.submitCreate(Path.of(params.get("dir")), Path.of(params.get("file")), params.get("content"));
            return null;
        });
        DevConsoleManager.register("roq-submit-delete", params -> {
            contentService.submitDelete(Path.of(params.get("path")));
            return null;
        });
        BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();
        actions.actionBuilder().methodName("writeStatus").function(
                params -> CompletableFuture.completedFuture(contentService.getStatus())).build();
        return actions;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    BuildTimeActionBuildItem registerGitActions(RoqEditorConfig editorConfig,
            RoqSiteConfig siteConfig,
            LaunchModeBuildItem launchModeBuildItem,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {

        if (!editorConfig.sync().enabled() || launchModeBuildItem.getDevModeType().orElse(null) != DevModeType.LOCAL) {
            return null;
        }

        Path projectRoot;
        if (curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getWorkspaceModule() != null) {
            projectRoot = curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getWorkspaceModule().getModuleDir()
                    .toPath();
        } else {
            projectRoot = Path.of(".").toAbsolutePath();
        }

        GitSyncService gitService = new GitSyncServiceImpl(editorConfig, siteConfig, projectRoot.toFile());
        return RoqEditorGitBuildActions.register(gitService);
    }
}
