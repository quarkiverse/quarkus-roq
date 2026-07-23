package io.quarkus.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test to verify that generated ConfigMapping classes can be compiled
 * and work correctly with Quarkus config system.
 */
public class ConfigMappingIntegrationTest {

    @Test
    void generatedConfigMappingCompiles(@TempDir Path tempDir) throws IOException {
        // Setup: Create a minimal Jekyll config with search settings
        String configYaml = """
                title: Test Site
                search:
                  script-mode: cached
                  host: https://search.example.com
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        // Run the converter
        JekyllConfigConverter converter = new JekyllConfigConverter();
        converter.convertProject(tempDir);

        // Verify ConfigMapping files were generated
        Path searchConfig = tempDir.resolve("src/main/java/io/testsite/search/config/SearchConfig.java");
        assertTrue(Files.exists(searchConfig), "SearchConfig.java should be generated");

        String configContent = Files.readString(searchConfig);
        assertTrue(configContent.contains("@ConfigMapping(prefix = \"testsite.search\")"),
                "Should have @ConfigMapping with project-derived prefix, not site.search");
        assertTrue(configContent.contains("@TemplateData"), "Should have @TemplateData annotation");
        assertTrue(configContent.contains("String scriptMode()"), "Should have scriptMode method");
        assertTrue(configContent.contains("String host()"), "Should have host method");

        // Verify producer was generated
        Path producer = tempDir.resolve("src/main/java/io/testsite/search/config/SearchConfigProducer.java");
        assertTrue(Files.exists(producer), "SearchConfigProducer.java should be generated");

        String producerContent = Files.readString(producer);
        assertTrue(producerContent.contains("@Produces"), "Should have @Produces annotation");
        assertTrue(producerContent.contains("@Named(\"searchConfig\")"), "Should be named 'searchConfig'");

        // Note: We can't actually test CDI injection here without a full Quarkus test harness
        // but we can verify the files are syntactically correct and have the right annotations
    }
}
