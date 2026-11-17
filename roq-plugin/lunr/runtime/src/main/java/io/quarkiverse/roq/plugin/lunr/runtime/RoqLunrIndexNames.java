package io.quarkiverse.roq.plugin.lunr.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;

/**
 * Parsing and resolution of search index names.
 */
public final class RoqLunrIndexNames {

    /**
     * The index a page belongs to when it does not declare {@link RoqLunrKeys#SEARCH_INDEXES}.
     */
    public static final String DEFAULT = "default";

    /**
     * Reserved trigger value expanding to every existing index.
     */
    public static final String ALL = "all";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private RoqLunrIndexNames() {
    }

    /**
     * @param value the {@link RoqLunrKeys#SEARCH_INDEXES} front matter value (string, list or null)
     * @return the indexes a page belongs to, {@link #DEFAULT} when none is declared
     */
    public static List<String> membership(Object value) {
        final List<String> names = RoqTemplateExtension.asStrings(value);
        return names.isEmpty() ? List.of(DEFAULT) : names;
    }

    /**
     * Resolve the indexes requested by a search trigger against the existing ones.
     *
     * @param requested the trigger value (string, list or null), {@link #ALL} expands to every known index
     * @param known the existing index names
     * @return the known names in requested order without duplicates, and the unknown ones
     */
    static Resolution resolve(Object requested, Collection<String> known) {
        final Set<String> names = new LinkedHashSet<>();
        final List<String> unknown = new ArrayList<>();
        for (String name : RoqTemplateExtension.asStrings(requested)) {
            if (ALL.equals(name)) {
                names.addAll(known);
            } else if (known.contains(name)) {
                names.add(name);
            } else if (!unknown.contains(name)) {
                unknown.add(name);
            }
        }
        return new Resolution(List.copyOf(names), List.copyOf(unknown));
    }

    /**
     * Index names are rendered into HTML attributes and an inline script, so they are restricted to
     * letters, digits, {@code -} and {@code _}. {@link #ALL} is reserved.
     */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches() && !ALL.equals(name);
    }

    record Resolution(List<String> names, List<String> unknown) {
    }
}
