package io.quarkiverse.roq.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.migration.engine.ProjectDependency;

class MvnpmDependenciesTest {

    @Test
    void mapsNpmPackagesToMvnpmArtifactsInProvidedScope() {
        assertThat(MvnpmDependencies.dependency("@fontsource/open-sans", "5.0.29"))
                .isEqualTo(new ProjectDependency("org.mvnpm.at.fontsource", "open-sans", "5.0.29", "provided"));
        assertThat(MvnpmDependencies.dependency("prismjs", "1.29.0"))
                .isEqualTo(new ProjectDependency("org.mvnpm", "prismjs", "1.29.0", "provided"));
    }

    @Test
    void findsThePackageOfAnImport() {
        assertThat(MvnpmDependencies.npmPackage("@fontsource/open-sans/300.css")).isEqualTo("@fontsource/open-sans");
        assertThat(MvnpmDependencies.npmPackage("@fontsource/open-sans")).isEqualTo("@fontsource/open-sans");
        assertThat(MvnpmDependencies.npmPackage("prismjs/themes/prism.css")).isEqualTo("prismjs");
    }

    @Test
    void readsTheInstalledVersionFromTheLockFile() throws Exception {
        String packageJson = "{\"dependencies\": {\"prismjs\": \"^1.29.0\"}}";
        String lock = "{\"packages\": {\"node_modules/prismjs\": {\"version\": \"1.30.0\"}}}";

        assertThat(MvnpmDependencies.version(lock, packageJson, "prismjs")).isEqualTo("1.30.0");
    }

    @Test
    void readsAVersion1LockFile() throws Exception {
        String lock = "{\"dependencies\": {\"prismjs\": {\"version\": \"1.28.0\"}}}";

        assertThat(MvnpmDependencies.version(lock, null, "prismjs")).isEqualTo("1.28.0");
    }

    @Test
    void fallsBackToTheVersionPackageJsonAsksFor() throws Exception {
        String packageJson = "{\"dependencies\": {\"prismjs\": \"^1.29.0\"}, \"devDependencies\": {\"other\": \"latest\"}}";

        assertThat(MvnpmDependencies.version(null, packageJson, "prismjs")).isEqualTo("1.29.0");
        assertThat(MvnpmDependencies.version(null, packageJson, "other")).isNull();
        assertThat(MvnpmDependencies.version(null, packageJson, "missing")).isNull();
        assertThat(MvnpmDependencies.version(null, null, "prismjs")).isNull();
    }
}
