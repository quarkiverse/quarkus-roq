package io.quarkiverse.roq.migration.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Facts about the site gathered by {@link SiteMigrator#analyze} and refined by later stages,
 * so stages share data in memory instead of through marker files.
 */
public class SiteAnalysis {

    private String projectName;
    private final Set<String> extensions = new LinkedHashSet<>();
    private final Set<ProjectDependency> dependencies = new LinkedHashSet<>();
    private final List<String> configMappings = new ArrayList<>();

    /**
     * @return the artifact id for the Roq project, e.g. {@code my-blog-website}
     */
    public String projectName() {
        return projectName;
    }

    public void projectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * @return extensions to add to the project, in {@code roq add} notation (e.g. {@code plugin:tagging}) or as GAVs
     */
    public Set<String> extensions() {
        return extensions;
    }

    public boolean hasExtension(String extension) {
        return extensions.contains(extension);
    }

    /**
     * @return other libraries to add to the project, such as mvnpm packages for the web bundler
     */
    public Set<ProjectDependency> dependencies() {
        return dependencies;
    }

    /**
     * @return ConfigMapping section names generated from the site configuration
     */
    public List<String> configMappings() {
        return configMappings;
    }
}
