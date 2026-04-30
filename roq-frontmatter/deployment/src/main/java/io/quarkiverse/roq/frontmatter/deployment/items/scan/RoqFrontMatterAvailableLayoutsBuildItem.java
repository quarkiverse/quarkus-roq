package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.LayoutRef;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * All available layout templates indexed by their template ID,
 * used for layout resolution during template processing.
 */
public final class RoqFrontMatterAvailableLayoutsBuildItem extends SimpleBuildItem {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterAvailableLayoutsBuildItem.class);

    private final Map<String, SourceFile> layoutsById;

    public RoqFrontMatterAvailableLayoutsBuildItem(Map<String, SourceFile> layoutsById) {
        this.layoutsById = Map.copyOf(layoutsById);
    }

    public Map<String, SourceFile> layoutsById() {
        return layoutsById;
    }

    /**
     * Resolve a layout reference from front matter into a canonical layout ID.
     * Handles legacy syntax, {@code theme-layout:} direct matching, cross-theme references,
     * and simple name resolution with own-theme awareness.
     *
     * @param activeTheme the active theme name
     * @param sourceTheme which theme the calling layout belongs to (empty for content/user layouts)
     * @param ref the layout reference (value + whether it came from {@code theme-layout:})
     * @return the resolved layout ID, or null if layoutValue is null/blank
     * @throws RoqLayoutNotFoundException if the layout cannot be found
     * @throws RoqThemeConfigurationException if theme configuration is invalid
     */
    public String resolveLayoutId(Optional<String> activeTheme, Optional<String> sourceTheme, LayoutRef ref) {
        if (ref.value() == null || ref.value().isBlank()) {
            return null;
        }

        String layoutValue = ref.value();
        String value = removeExtension(layoutValue);

        // 1. Legacy: starts with "theme-layouts/" or "layouts/" -> warn, direct match or fail
        if (value.startsWith(THEME_LAYOUTS_DIR)) {
            String simple = stripThemeDirPrefix(value);
            LOGGER.warnf(
                    "DEPRECATED: Using full path '%s' in layout is deprecated. Use 'theme-layout: %s' instead.",
                    value, simple);
            return findOrFail(value, value);
        }
        if (value.startsWith(LAYOUTS_DIR)) {
            LOGGER.warnf(
                    "DEPRECATED: Using full path '%s' in layout is deprecated. Use 'layout: %s' instead.",
                    value, value.substring(LAYOUTS_DIR.length()));
            return findOrFail(value, value);
        }

        // 2. Legacy: contains ":theme/" -> warn, strip, continue
        if (value.contains(":theme/")) {
            String stripped = value.replaceFirst(":theme/", "");
            LOGGER.warnf(
                    "DEPRECATED: ':theme' in layout '%s' is deprecated. Use 'layout: %s' instead.",
                    value, stripped);
            value = stripped;
        }

        // theme-layout: resolution (scopeToTheme = true)
        if (ref.scopeToTheme()) {
            // 3. theme-layout: with "/" -> direct match theme-layouts/X/foo
            if (value.contains("/")) {
                return findOrFail(THEME_LAYOUTS_DIR + value, layoutValue);
            }
            // 4. theme-layout: no "/" + from theme layout -> theme-layouts/{own}/foo
            if (sourceTheme.isPresent()) {
                return findOrFail(THEME_LAYOUTS_DIR + sourceTheme.get() + "/" + value, layoutValue);
            }
            // 5. theme-layout: no "/" + from content/user -> theme-layouts/{active}/foo
            if (activeTheme.isEmpty() || activeTheme.get().isBlank()) {
                throw new RoqThemeConfigurationException(
                        RoqException.builder("No theme configured")
                                .detail("'theme-layout: %s' requires a theme, but no theme dependency was detected."
                                        .formatted(layoutValue))
                                .hint("Add a Roq theme dependency to your project, or use 'layout:' instead of 'theme-layout:'."));
            }
            return findOrFail(THEME_LAYOUTS_DIR + activeTheme.get() + "/" + value, layoutValue);
        }

        // layout: resolution (scopeToTheme = false)
        List<String> candidates = new ArrayList<>();

        if (value.contains("/")) {
            // 6. layout: with "/" -> layouts/X/foo -> theme-layouts/X/foo
            candidates.add(LAYOUTS_DIR + value);
            candidates.add(THEME_LAYOUTS_DIR + value);
        } else if (sourceTheme.isEmpty()) {
            // 7. Content/user: layouts/foo -> layouts/{active}/foo -> theme-layouts/{active}/foo
            candidates.add(LAYOUTS_DIR + value);
            if (activeTheme.isPresent()) {
                candidates.add(LAYOUTS_DIR + activeTheme.get() + "/" + value);
                candidates.add(THEME_LAYOUTS_DIR + activeTheme.get() + "/" + value);
            }
        } else if (sourceTheme.equals(activeTheme)) {
            // 8. Theme layout, own == active: layouts/foo -> theme-layouts/{own}/foo
            candidates.add(LAYOUTS_DIR + value);
            candidates.add(THEME_LAYOUTS_DIR + sourceTheme.get() + "/" + value);
        } else {
            // 9. Theme layout, own != active: layouts/{own}/foo -> theme-layouts/{own}/foo
            candidates.add(LAYOUTS_DIR + sourceTheme.get() + "/" + value);
            candidates.add(THEME_LAYOUTS_DIR + sourceTheme.get() + "/" + value);
        }

        return firstMatchOrFail(layoutValue, candidates);
    }

    private String findOrFail(String id, String errorName) {
        if (layoutsById.containsKey(id)) {
            return id;
        }
        throw new RoqLayoutNotFoundException(buildErrorBuilder(errorName, List.of(id)));
    }

    private String firstMatchOrFail(String layoutValue, List<String> candidates) {
        for (String candidate : candidates) {
            if (layoutsById.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new RoqLayoutNotFoundException(buildErrorBuilder(layoutValue, candidates));
    }

    private static String stripThemeDirPrefix(String value) {
        String afterDir = value.substring(THEME_LAYOUTS_DIR.length());
        int slash = afterDir.indexOf('/');
        return slash >= 0 ? afterDir.substring(slash + 1) : afterDir;
    }

    private RoqException.Builder buildErrorBuilder(String originalName, List<String> candidates) {
        String available = layoutsById.keySet().stream()
                .limit(20)
                .collect(Collectors.joining(", ", "[", layoutsById.size() > 20 ? ", ...]" : "]"));
        String tried = String.join("\n", candidates.stream().map(s -> "  - " + s).toList());
        return RoqException.builder("Layout not found")
                .detail("Layout '%s' could not be resolved. Tried:\n%s".formatted(originalName, tried))
                .hint("Available layouts: %s".formatted(available));
    }
}
