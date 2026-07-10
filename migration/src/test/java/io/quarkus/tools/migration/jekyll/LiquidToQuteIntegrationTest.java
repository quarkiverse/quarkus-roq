package io.quarkus.tools.migration.jekyll;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateException;
import io.quarkus.tools.LiquidToQuteConverter;

class LiquidToQuteIntegrationTest {

    static Stream<String> liquidTemplateFiles() throws IOException, URISyntaxException {
        Path dir = Paths.get(LiquidToQuteIntegrationTest.class.getClassLoader()
                .getResource("qute-validation").toURI());
        return Files.list(dir)
                .filter(p -> p.toString().endsWith(".html"))
                .map(p -> p.getFileName().toString())
                .sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("liquidTemplateFiles")
    void converterOutputParsesAsValidQute(String filename) throws Exception {
        Path inputFile = Paths.get(getClass().getClassLoader()
                .getResource("qute-validation/" + filename).toURI());
        String jekyllInput = Files.readString(inputFile);

        LiquidToQuteConverter converter = new LiquidToQuteConverter();
        String quteOutput = converter.convert(jekyllInput);

        quteOutput = stripFrontmatter(quteOutput);

        Engine engine = Engine.builder()
                .addDefaults()
                .build();
        try {
            engine.parse(quteOutput);
        } catch (TemplateException e) {
            fail("Converter output for " + filename + " is not valid Qute:\n"
                    + e.getMessage() + "\n\nConverter output:\n" + quteOutput);
        }
    }

    private static String stripFrontmatter(String content) {
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end >= 0) {
                return content.substring(end + 3).stripLeading();
            }
        }
        return content;
    }
}
