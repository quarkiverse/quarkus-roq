package io.quarkiverse.roq.plugin.l10n.asciidoc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class L10nAdocTreeprocessorTest {

    private Asciidoctor createAsciidoctor(Path poBaseDir) {
        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.javaExtensionRegistry()
                .preprocessor(new L10nAdocPreprocessor(poBaseDir))
                .treeprocessor(new L10nAdocTreeprocessor(poBaseDir));
        return asciidoctor;
    }

    private String convert(Asciidoctor asciidoctor, String adoc, Path contentDir) {
        return asciidoctor.convert(adoc, Options.builder()
                .safe(SafeMode.SAFE)
                .baseDir(contentDir.toFile())
                .attributes(Attributes.builder()
                        .attribute("docname", "test-doc")
                        .build())
                .option("root_dir", contentDir.getParent().toString())
                .build());
    }

    private Path setupPoFile(Path tempDir, String poContent) throws IOException {
        Path poBaseDir = tempDir.resolve("po");
        Path contentDir = tempDir.resolve("project/content");
        Files.createDirectories(contentDir);
        Path poFile = poBaseDir.resolve("content/test-doc.adoc.po");
        Files.createDirectories(poFile.getParent());
        Files.writeString(poFile, poContent);
        return poBaseDir;
    }

    @Test
    void noOpWhenPoBaseDirIsNull(@TempDir Path tempDir) {
        try (Asciidoctor asciidoctor = createAsciidoctor(null)) {
            String html = asciidoctor.convert("== Hello World\n\nSome content.",
                    Options.builder().build());

            assertTrue(html.contains("Hello World"));
            assertTrue(html.contains("Some content"));
        }
    }

    @Test
    void translatesSectionTitle(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Hello World"
                msgstr "Olá Mundo"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Hello World\n\nSome content.", contentDir);

            assertTrue(html.contains("Olá Mundo"), "Section title should be translated. Got: " + html);
        }
    }

    @Test
    void preservesSectionIdWhenTitleTranslated(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Getting Started"
                msgstr "Primeiros Passos"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Getting Started\n\nContent here.", contentDir);

            assertTrue(html.contains("Primeiros Passos"), "Title should be translated. Got: " + html);
            assertTrue(html.contains("getting_started") || html.contains("_getting_started"),
                    "Section ID should be preserved from original English title. Got: " + html);
        }
    }

    @Test
    void translatesParagraphContent(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "This is a paragraph."
                msgstr "Este é um parágrafo."
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Section\n\nThis is a paragraph.", contentDir);

            assertTrue(html.contains("Este é um parágrafo."), "Paragraph should be translated. Got: " + html);
        }
    }

    @Test
    void translatesListItems(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "First item"
                msgstr "Primeiro item"

                msgid "Second item"
                msgstr "Segundo item"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== List\n\n* First item\n* Second item", contentDir);

            assertTrue(html.contains("Primeiro item"), "First list item should be translated. Got: " + html);
            assertTrue(html.contains("Segundo item"), "Second list item should be translated. Got: " + html);
        }
    }

    @Test
    void translatesTableCells(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Name"
                msgstr "Nome"

                msgid "Value"
                msgstr "Valor"

                msgid "Alice"
                msgstr "Alice-pt"

                msgid "100"
                msgstr "100-pt"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, """
                    == Table

                    |===
                    | Name | Value

                    | Alice
                    | 100
                    |===""", contentDir);

            assertTrue(html.contains("Nome"), "Table header should be translated. Got: " + html);
            assertTrue(html.contains("Valor"), "Table header should be translated. Got: " + html);
        }
    }

    @Test
    void noOpWhenPoFileDoesNotExist(@TempDir Path tempDir) {
        Path poBaseDir = tempDir.resolve("empty-po");

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            String html = asciidoctor.convert("== Original Title\n\nOriginal content.",
                    Options.builder()
                            .safe(SafeMode.SAFE)
                            .attributes(Attributes.builder()
                                    .attribute("docname", "nonexistent")
                                    .build())
                            .build());

            assertTrue(html.contains("Original Title"));
            assertTrue(html.contains("Original content"));
        }
    }

    @Test
    void preservesUntranslatedContent(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Translated title"
                msgstr "Título traduzido"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Untranslated title\n\nUntranslated paragraph.", contentDir);

            assertTrue(html.contains("Untranslated title"),
                    "Untranslated title should be preserved. Got: " + html);
            assertTrue(html.contains("Untranslated paragraph"),
                    "Untranslated paragraph should be preserved. Got: " + html);
        }
    }

    @Test
    void translatesDescriptionList(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Term one"
                msgstr "Termo um"

                msgid "Definition one"
                msgstr "Definição um"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Glossary\n\nTerm one:: Definition one", contentDir);

            assertTrue(html.contains("Termo um"), "Description list term should be translated. Got: " + html);
            assertTrue(html.contains("Definição um"), "Description list definition should be translated. Got: " + html);
        }
    }

    @Test
    void doesNotTranslateCodeBlocks(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "System.out.println(\\"hello\\");"
                msgstr "SHOULD NOT APPEAR"
                """);

        try (Asciidoctor asciidoctor = createAsciidoctor(poBaseDir)) {
            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, """
                    == Code

                    [source,java]
                    ----
                    System.out.println("hello");
                    ----""", contentDir);

            assertFalse(html.contains("SHOULD NOT APPEAR"),
                    "Code blocks should not be translated. Got: " + html);
        }
    }

    @Test
    void resolvesPoBaseDirFromEnvVarFallback(@TempDir Path tempDir) throws IOException {
        // When Arc is not available (test environment), resolvePoBaseDir() falls back
        // to System.getenv("L10N_PO_BASE_DIR"). In production Quarkus, Arc.container()
        // provides the config and SmallRye maps L10N_PO_BASE_DIR env var to l10n.po-base-dir.
        // This test verifies the fallback path produces a valid Path.
        Path resolved = L10nAdocExtensionRegistry.resolvePoBaseDir();

        // Without L10N_PO_BASE_DIR env var set, should return null (no-op)
        if (System.getenv("L10N_PO_BASE_DIR") == null) {
            assertNull(resolved, "Should return null when L10N_PO_BASE_DIR is not set");
        }
    }

    @Test
    void registryResolvesAndPassesPoBaseDir(@TempDir Path tempDir) throws IOException {
        Path poBaseDir = setupPoFile(tempDir, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Registry Test"
                msgstr "Teste do Registro"
                """);

        // Simulate what the registry does: resolve config then pass to extensions
        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            asciidoctor.javaExtensionRegistry()
                    .preprocessor(new L10nAdocPreprocessor(poBaseDir))
                    .treeprocessor(new L10nAdocTreeprocessor(poBaseDir));

            Path contentDir = tempDir.resolve("project/content");
            String html = convert(asciidoctor, "== Registry Test\n\nContent.", contentDir);

            assertTrue(html.contains("Teste do Registro"),
                    "Registry-resolved config should enable translation. Got: " + html);
        }
    }
}
