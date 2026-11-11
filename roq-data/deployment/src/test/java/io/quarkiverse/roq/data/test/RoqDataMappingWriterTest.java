package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.runtime.DataMappingWriter;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.data.test.util.Foo;
import io.quarkiverse.roq.data.test.util.Foos;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class RoqDataMappingWriterTest {

    static final Path SITE = createSite();

    @RegisterExtension
    static final QuarkusExtensionTest quarkusUnitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Foo.class, Foos.class, Baz.class, Gone.class)
                    .add(new StringAsset("quarkus.roq.dir=target/roq-data-writer-test"),
                            "application.properties"));

    @DataMapping("baz")
    public static class Baz {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @DataMapping("gone")
    public record Gone(String name) {
    }

    @Inject
    DataMappingWriter writer;

    @Inject
    Foos foos;

    @Inject
    Baz baz;

    @Test
    void savesToTheSourceFile() {
        // unchanged data, nothing is written
        assertThat(writer.save(baz)).isEqualTo(SITE.resolve("data/baz.yml").toAbsolutePath().normalize());
        assertThatThrownBy(() -> writer.save("not mapped")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveJson() throws IOException {
        writer.save(new Foo("Changed"));
        assertThat(read("data/foo.json")).isEqualTo("""
                {
                    "name": "Changed"
                }
                """);
    }

    @Test
    void saveArrayFile() throws IOException {
        List<Foo> list = new java.util.ArrayList<>(foos.list());
        list.remove(1);
        list.add(new Foo("Four"));
        writer.save(new Foos(list));
        assertThat(read("data/foos.yaml")).isEqualTo("""
                # Heroes
                - name: Super Heroes 1 from Yaml # first
                # Third
                - name: Super Heroes 3 from Yaml
                - name: Four
                """);
    }

    @Test
    void saveProxiedBean() throws IOException {
        assertThat(baz).isInstanceOf(ClientProxy.class);
        writer.save(baz);
        assertThat(read("data/baz.yml")).isEqualTo(BAZ);
        baz.setName("Renamed");
        writer.save(baz);
        assertThat(read("data/baz.yml")).isEqualTo(BAZ.replace("Super Heroes from Yml", "Renamed"));
    }

    @Test
    void refuseMissingSourceFile() throws IOException {
        Path gone = SITE.resolve("data/gone.yml");
        Files.delete(gone);
        assertThatThrownBy(() -> writer.save(new Gone("back")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer exists");
        assertThat(gone).doesNotExist();
    }

    private static final String BAZ = """
            # Baz
            name: Super Heroes from Yml # the name
            """;

    private static String read(String path) throws IOException {
        return Files.readString(SITE.resolve(path), StandardCharsets.UTF_8);
    }

    private static Path createSite() {
        try {
            // must be relative to the project
            Path site = Path.of("target/roq-data-writer-test");
            Path data = Files.createDirectories(site.resolve("data"));
            Files.writeString(data.resolve("foo.json"), """
                    {
                        "name": "Super Heroes from Json"
                    }
                    """);
            Files.writeString(data.resolve("foos.yaml"), """
                    # Heroes
                    - name: Super Heroes 1 from Yaml # first
                    # Second
                    - name: Super Heroes 2 from Yaml
                    # Third
                    - name: Super Heroes 3 from Yaml
                    """);
            Files.writeString(data.resolve("baz.yml"), BAZ);
            Files.writeString(data.resolve("gone.yml"), "name: Gone\n");
            return site;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
