package io.quarkiverse.roq.editor.deployment;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorJsonRPCService;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
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
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.devmode.IdeHelper;

class RoqEditorProcessor {

    private static final String FEATURE = "roq-editor";
    static volatile ConsoleStateManager.ConsoleContext context;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
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
        context.reset(new ConsoleCommand('c', "Open the Roq Editor in a browser", null,
                () -> IdeHelper.openBrowser(rp, np, protocol, "/q/dev-ui/quarkus-roq-editor/roq-editor", host, port)));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(
            RoqEditorConfig config,
            CurateOutcomeBuildItem bi,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList) {
        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Roq Editor")
                .componentLink("qwc-roq-editor.js")
                .icon("font-awesome-solid:pencil"));
        List<String> markups = new ArrayList<>(markupList.stream().map(RoqFrontMatterQuteMarkupBuildItem::name).toList());
        markups.add("html");
        pageBuildItem.addBuildTimeData("markups", markups);
        pageBuildItem.addBuildTimeData("config", config);
        return pageBuildItem;
    }

    @BuildStep
    JsonRPCProvidersBuildItem createJsonRPCServiceForCache() {
        return new JsonRPCProvidersBuildItem(RoqEditorJsonRPCService.class);
    }
}
