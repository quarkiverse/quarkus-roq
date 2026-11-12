package io.quarkiverse.roq.migration.engine;

/**
 * A library the migrated project needs that is not a Quarkus extension, such as a font or a JavaScript package for the
 * web bundler. The {@link Scaffolder} adds it to the project's build.
 *
 * @param scope a Maven scope, e.g. {@code provided}; null for the default
 */
public record ProjectDependency(String groupId, String artifactId, String version, String scope) {

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + (scope == null ? "" : " (" + scope + ")");
    }
}
