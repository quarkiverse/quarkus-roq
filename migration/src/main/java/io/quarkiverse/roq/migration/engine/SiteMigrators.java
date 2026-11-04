package io.quarkiverse.roq.migration.engine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The site migrators, by the site type each one migrates, and the choice of the one for a site by detecting its type.
 * <p>
 * A migrator converts one site, so each choice makes a new one.
 */
public final class SiteMigrators {

    private static final SiteMigrators BUILT_IN = new SiteMigrators(Map.of());

    private final Map<SiteType, Supplier<SiteMigrator>> migrators;

    /**
     * @param migrators makes a migrator for each type that has one
     */
    public SiteMigrators(Map<SiteType, Supplier<SiteMigrator>> migrators) {
        this.migrators = migrators.isEmpty() ? Map.of() : new EnumMap<>(migrators);
    }

    /**
     * @return the migrators that come with Roq
     */
    public static SiteMigrators builtIn() {
        return BUILT_IN;
    }

    /**
     * @return the types of site there is a migrator for, in {@link SiteType} order
     */
    public Set<SiteType> types() {
        return Collections.unmodifiableSet(migrators.keySet());
    }

    /**
     * @return a new migrator for the type; empty if there is none
     */
    public Optional<SiteMigrator> forType(SiteType type) {
        Supplier<SiteMigrator> migrator = migrators.get(type);
        if (migrator == null) {
            return Optional.empty();
        }
        SiteMigrator made = migrator.get();
        if (made.type() != type) {
            throw new IllegalStateException("The migrator for " + type + " migrates " + made.type());
        }
        return Optional.of(made);
    }

    /**
     * Chooses the migrator for a site, from the type of site detected.
     *
     * @throws MigrationException if the type cannot be detected, or there is no migrator for it
     */
    public SiteMigrator choose(Path sourceDir) throws MigrationException {
        SiteType type = SiteType.detect(sourceDir)
                .orElseThrow(() -> new MigrationException("Could not tell which generator built " + sourceDir));
        if (type == SiteType.ROQ) {
            throw new MigrationException(sourceDir + " is a Roq site already");
        }
        Optional<SiteMigrator> migrator = forType(type);
        if (migrator.isEmpty()) {
            String others = migrators.isEmpty() ? "" : "; only " + names(types()) + " sites can";
            throw new MigrationException(sourceDir + " looks like a " + type.displayName() + " site, but "
                    + type.displayName() + " sites cannot be migrated yet" + others);
        }
        return migrator.get();
    }

    /**
     * @return e.g. {@code Jekyll, Hugo and Gatsby}
     */
    private static String names(Set<SiteType> types) {
        String names = types.stream().map(SiteType::displayName).collect(Collectors.joining(", "));
        int last = names.lastIndexOf(", ");
        return last < 0 ? names : names.substring(0, last) + " and " + names.substring(last + 2);
    }
}
