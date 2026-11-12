package io.quarkiverse.roq.migration.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.migration.engine.ProjectDependency;

class RoqScaffolderTest {

    @TempDir
    Path dir;

    static final List<ProjectDependency> PRISM = List.of(new ProjectDependency("org.mvnpm", "prismjs", "1.29.0",
            "provided"));

    @Test
    void addsDependenciesToTheProjectsOwnDependencies() throws IOException {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, """
                <project>
                    <dependencyManagement>
                        <dependencies>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                    </dependencies>
                </project>
                """);

        assertThat(RoqScaffolder.addDependencies(pom, PRISM)).isTrue();

        assertThat(pom).hasContent("""
                <project>
                    <dependencyManagement>
                        <dependencies>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.mvnpm</groupId>
                            <artifactId>prismjs</artifactId>
                            <version>1.29.0</version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
    }

    @Test
    void addsEachDependencyOnce() throws IOException {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, "<project>\n    <dependencies>\n    </dependencies>\n</project>\n");

        RoqScaffolder.addDependencies(pom, PRISM);
        RoqScaffolder.addDependencies(pom, PRISM);

        assertThat(Files.readString(pom)).containsOnlyOnce("<artifactId>prismjs</artifactId>");
    }

    @Test
    void reportsAPomWithoutDependencies() throws IOException {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, "<project/>\n");

        assertThat(RoqScaffolder.addDependencies(pom, PRISM)).isFalse();
    }
}
