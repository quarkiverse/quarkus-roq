package io.quarkiverse.roq.migration;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.roq.migration.engine.ProjectDependency;

/**
 * Maps the npm packages a migrated site used to mvnpm dependencies, which the Quarkus Web Bundler resolves imports
 * from as it would from {@code node_modules}.
 */
public final class MvnpmDependencies {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MvnpmDependencies() {
    }

    /**
     * @param npmPackage the npm package name, e.g. {@code @fontsource/open-sans}
     * @return its mvnpm artifact, e.g. {@code org.mvnpm.at.fontsource:open-sans}, in provided scope: the web bundler
     *         only needs it at build time
     */
    public static ProjectDependency dependency(String npmPackage, String version) {
        boolean scoped = npmPackage.startsWith("@");
        String groupId = scoped ? "org.mvnpm.at." + npmPackage.substring(1, npmPackage.indexOf('/')) : "org.mvnpm";
        String artifactId = scoped ? npmPackage.substring(npmPackage.indexOf('/') + 1) : npmPackage;
        return new ProjectDependency(groupId, artifactId, version, "provided");
    }

    /**
     * @param module what an import names, e.g. {@code @fontsource/open-sans/300.css}
     * @return the package it comes from, e.g. {@code @fontsource/open-sans}
     */
    public static String npmPackage(String module) {
        String[] parts = module.split("/");
        return module.startsWith("@") && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
    }

    /**
     * @param packageLock the content of package-lock.json, or null
     * @param packageJson the content of package.json, or null
     * @return the version installed according to package-lock.json, else the version package.json asks for without
     *         its range operator (^5.0.0 is 5.0.0); null if neither says
     */
    public static String version(String packageLock, String packageJson, String npmPackage) throws IOException {
        if (packageLock != null) {
            JsonNode root = JSON.readTree(packageLock);
            // Lockfile v2 and v3 list packages by path, v1 by name
            JsonNode installed = root.path("packages").path("node_modules/" + npmPackage).path("version");
            if (installed.isMissingNode()) {
                installed = root.path("dependencies").path(npmPackage).path("version");
            }
            if (installed.isTextual()) {
                return installed.asText();
            }
        }
        if (packageJson != null) {
            JsonNode root = JSON.readTree(packageJson);
            for (String section : List.of("dependencies", "devDependencies")) {
                JsonNode wanted = root.path(section).path(npmPackage);
                if (wanted.isTextual()) {
                    String version = wanted.asText().replaceFirst("^[\\^~>=<\\s]+", "");
                    if (version.matches("\\d+(\\.\\d+){0,2}([-+].*)?")) {
                        return version;
                    }
                }
            }
        }
        return null;
    }
}
