package io.quarkiverse.roq.migration.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteMigratorsTest {

    @TempDir
    Path tmp;

    /**
     * Migrates nothing; there is one per type.
     */
    static class NoOpMigrator extends SiteMigrator {
        private final SiteType type;

        NoOpMigrator(SiteType type) {
            this.type = type;
        }

        @Override
        public SiteType type() {
            return type;
        }

        @Override
        protected void preflight(SourceSite source) {
        }

        @Override
        protected void analyze(SourceSite source, SiteAnalysis analysis) {
        }

        @Override
        protected void restructure(TargetSite site) {
        }

        @Override
        protected void convertFrontMatter(TargetSite site) {
        }

        @Override
        protected void convertConfig(TargetSite site) {
        }

        @Override
        protected void convertTemplates(TargetSite site) {
        }

        @Override
        protected void convertContent(TargetSite site) {
        }

        @Override
        protected void convertAssets(TargetSite site) {
        }

        @Override
        protected void generateCode(TargetSite site) {
        }

        @Override
        protected void checkResult(TargetSite site) {
        }

        @Override
        protected void validate(TargetSite site, ReferenceSite reference) {
        }
    }

    final SiteMigrators migrators = new SiteMigrators(Map.of(
            SiteType.JEKYLL, () -> new NoOpMigrator(SiteType.JEKYLL),
            SiteType.GATSBY, () -> new NoOpMigrator(SiteType.GATSBY)));

    @Test
    void makesANewMigratorEachTime() {
        SiteMigrator first = migrators.forType(SiteType.JEKYLL).orElseThrow();

        assertThat(first.type()).isEqualTo(SiteType.JEKYLL);
        assertThat(migrators.forType(SiteType.JEKYLL).orElseThrow()).isNotSameAs(first);
        assertThat(migrators.forType(SiteType.HUGO)).isEmpty();
        assertThat(migrators.types()).containsExactly(SiteType.JEKYLL, SiteType.GATSBY);
    }

    @Test
    void choosesTheMigratorForTheTypeDetected() throws Exception {
        assertThat(migrators.choose(site("gatsby-config.js")).type()).isEqualTo(SiteType.GATSBY);
    }

    @Test
    void saysWhyItCannotChoose() throws IOException {
        Path hugo = site("hugo.toml");
        Path empty = site();
        Path roq = site("pom.xml");
        Files.writeString(roq.resolve("pom.xml"), "<artifactId>quarkus-roq</artifactId>");

        assertThatThrownBy(() -> migrators.choose(hugo)).isInstanceOf(MigrationException.class)
                .hasMessage(hugo + " looks like a Hugo site, but Hugo sites cannot be migrated yet; only Jekyll and "
                        + "Gatsby sites can");
        assertThatThrownBy(() -> migrators.choose(empty)).hasMessage("Could not tell which generator built " + empty);
        assertThatThrownBy(() -> migrators.choose(roq)).hasMessage(roq + " is a Roq site already");
        assertThatThrownBy(() -> new SiteMigrators(Map.of()).choose(hugo))
                .hasMessage(hugo + " looks like a Hugo site, but Hugo sites cannot be migrated yet");
    }

    @Test
    void refusesAMigratorRegisteredForAnotherType() {
        Supplier<SiteMigrator> jekyll = () -> new NoOpMigrator(SiteType.JEKYLL);
        SiteMigrators wrong = new SiteMigrators(Map.of(SiteType.HUGO, jekyll));

        assertThatThrownBy(() -> wrong.forType(SiteType.HUGO)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The migrator for hugo migrates jekyll");
    }

    @Test
    void hasNoBuiltInMigratorsYet() {
        assertThat(SiteMigrators.builtIn().types()).isEmpty();
    }

    private Path site(String... files) throws IOException {
        Path dir = Files.createTempDirectory(tmp, "site");
        for (String file : files) {
            Files.writeString(dir.resolve(file), "");
        }
        return dir;
    }
}
