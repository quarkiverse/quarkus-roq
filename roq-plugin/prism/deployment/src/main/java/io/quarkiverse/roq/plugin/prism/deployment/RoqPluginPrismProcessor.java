package io.quarkiverse.roq.plugin.prism.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqPluginPrismProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginPrismProcessor.class);
    private static final String FEATURE = "roq-plugin-prism";
    private static final String JS_ENDPOINT = "/static/bundle/prism.js";
    private static final String CSS_ENDPOINT = "/static/bundle/prism.css";
    private static final String AUTO_HIGHLIGHT = "if(document.readyState==='loading'){"
            + "document.addEventListener('DOMContentLoaded',function(){Prism.highlightAll();});"
            + "}else{Prism.highlightAll();}";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateBundle(RoqPluginPrismConfig config,
            BuildProducer<GeneratedStaticResourceBuildItem> generated) throws IOException {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final String version = readPrismVersion(cl);
        final String base = "META-INF/resources/_static/prismjs/" + version + "/";

        final JsonObject components = readJson(cl, base + "components.json");
        final JsonObject languagesMeta = components.getJsonObject("languages");

        final LinkedHashSet<String> resolved = resolveLanguages(config.languages(), languagesMeta);

        final StringBuilder js = new StringBuilder();
        js.append(read(cl, base + "components/prism-core.min.js"));
        for (String lang : resolved) {
            js.append('\n').append(read(cl, base + "components/prism-" + lang + ".min.js"));
        }
        js.append('\n').append(AUTO_HIGHLIGHT);

        final String themeName = config.theme();
        final String themePath = base + "themes/"
                + ("default".equals(themeName) ? "prism" : "prism-" + themeName) + ".min.css";
        if (cl.getResource(themePath) == null) {
            throw new ConfigurationException("Unknown Prism theme '" + themeName
                    + "' (no file at " + themePath + "). See https://prismjs.com/index.html#themes for available themes.");
        }
        final String css = read(cl, themePath);

        LOG.infof("Roq Prism: bundled %d Prism language(s) %s with theme '%s' — js=%d bytes, css=%d bytes",
                resolved.size(), resolved, themeName, js.length(), css.length());

        generated.produce(new GeneratedStaticResourceBuildItem(JS_ENDPOINT,
                js.toString().getBytes(StandardCharsets.UTF_8)));
        generated.produce(new GeneratedStaticResourceBuildItem(CSS_ENDPOINT,
                css.getBytes(StandardCharsets.UTF_8)));
    }

    static LinkedHashSet<String> resolveLanguages(List<String> requested, JsonObject languagesMeta) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String lang : requested) {
            visit(lang.trim(), languagesMeta, ordered, new ArrayList<>());
        }
        return ordered;
    }

    private static void visit(String lang, JsonObject languagesMeta,
            LinkedHashSet<String> ordered, List<String> path) {
        if (ordered.contains(lang)) {
            return;
        }
        if (path.contains(lang)) {
            throw new ConfigurationException("Cyclic Prism language requirement: " + path + " -> " + lang);
        }
        JsonObject meta = languagesMeta.getJsonObject(lang);
        if (meta == null) {
            throw new ConfigurationException("Unknown Prism language '" + lang
                    + "'. Use canonical names from prism's components.json (aliases like 'js' are not accepted).");
        }
        path.add(lang);
        Object require = meta.getValue("require");
        for (String dep : asList(require)) {
            visit(dep, languagesMeta, ordered, path);
        }
        path.remove(path.size() - 1);
        ordered.add(lang);
    }

    private static List<String> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String s) {
            return List.of(s);
        }
        if (value instanceof JsonArray arr) {
            List<String> out = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.getString(i));
            }
            return out;
        }
        return List.of();
    }

    private static String readPrismVersion(ClassLoader cl) throws IOException {
        try (InputStream in = cl.getResourceAsStream("META-INF/maven/org.mvnpm/prismjs/pom.properties")) {
            if (in == null) {
                throw new IllegalStateException("org.mvnpm:prismjs not on the deployment classpath");
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version");
        }
    }

    private static String read(ClassLoader cl, String path) throws IOException {
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject readJson(ClassLoader cl, String path) throws IOException {
        return new JsonObject(read(cl, path));
    }
}
