package io.quarkiverse.roq.plugin.l10n.asciidoc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class L10nAdocPoFileTest {

    @TempDir
    Path tempDir;

    private Path writePoFile(String content) throws IOException {
        Path poFile = tempDir.resolve("test.po");
        Files.writeString(poFile, content);
        return poFile;
    }

    @Test
    void translatesKnownMsgid() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Hello World"
                msgstr "Olá Mundo"
                """);

        var poFile = new L10nAdocPoFile(po);
        assertEquals("Olá Mundo", poFile.translate("Hello World"));
    }

    @Test
    void returnsNullForUnknownMsgid() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Hello"
                msgstr "Olá"
                """);

        var poFile = new L10nAdocPoFile(po);
        assertNull(poFile.translate("Unknown"));
    }

    @Test
    void skipsEmptyMsgstr() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Untranslated"
                msgstr ""
                """);

        var poFile = new L10nAdocPoFile(po);
        assertNull(poFile.translate("Untranslated"));
    }

    @Test
    void skipsFuzzyEntries() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                #, fuzzy
                msgid "Draft translation"
                msgstr "Tradução rascunho"
                """);

        var poFile = new L10nAdocPoFile(po);
        assertNull(poFile.translate("Draft translation"));
    }

    @Test
    void handlesMultilineMsgid() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid ""
                "This is a long "
                "multiline string"
                msgstr "Esta é uma string longa multilinha"
                """);

        var poFile = new L10nAdocPoFile(po);
        assertEquals("Esta é uma string longa multilinha",
                poFile.translate("This is a long multiline string"));
    }

    @Test
    void handlesMultipleEntries() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "First"
                msgstr "Primeiro"

                msgid "Second"
                msgstr "Segundo"

                msgid "Third"
                msgstr "Terceiro"
                """);

        var poFile = new L10nAdocPoFile(po);
        assertEquals("Primeiro", poFile.translate("First"));
        assertEquals("Segundo", poFile.translate("Second"));
        assertEquals("Terceiro", poFile.translate("Third"));
    }

    @Test
    void writeToOutputsEntriesInEncounteredOrder() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Third"
                msgstr "Terceiro"

                msgid "First"
                msgstr "Primeiro"

                msgid "Second"
                msgstr "Segundo"
                """);

        var poFile = new L10nAdocPoFile(po);
        poFile.addEntry("First");
        poFile.addEntry("Second");
        poFile.addEntry("Third");

        Path output = tempDir.resolve("output.po");
        poFile.writeTo(output);

        String content = Files.readString(output);
        int firstPos = content.indexOf("\"First\"");
        int secondPos = content.indexOf("\"Second\"");
        int thirdPos = content.indexOf("\"Third\"");

        assertTrue(firstPos < secondPos, "First should come before Second. Got:\n" + content);
        assertTrue(secondPos < thirdPos, "Second should come before Third. Got:\n" + content);
    }

    @Test
    void writeToRemovesObsoleteEntries() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Keep this"
                msgstr "Manter isso"

                msgid "Remove this"
                msgstr "Remover isso"
                """);

        var poFile = new L10nAdocPoFile(po);
        poFile.addEntry("Keep this");
        // "Remove this" is not encountered

        Path output = tempDir.resolve("output.po");
        poFile.writeTo(output);

        String content = Files.readString(output);
        assertTrue(content.contains("\"Keep this\""), "Kept entry should be present. Got:\n" + content);
        assertTrue(content.contains("\"Manter isso\""), "Translation should be preserved. Got:\n" + content);
        assertFalse(content.contains("\"Remove this\""), "Obsolete entry should be removed. Got:\n" + content);
    }

    @Test
    void hasChangesDetectsNewEntry() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Existing"
                msgstr "Existente"
                """);

        var poFile = new L10nAdocPoFile(po);
        poFile.addEntry("Existing");
        assertFalse(poFile.hasChanges(), "No changes when same entries");

        poFile.addEntry("New entry");
        assertTrue(poFile.hasChanges(), "Should detect new entry");
    }

    @Test
    void hasChangesDetectsRemovedEntry() throws IOException {
        Path po = writePoFile("""
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "Keep"
                msgstr "Manter"

                msgid "Remove"
                msgstr "Remover"
                """);

        var poFile = new L10nAdocPoFile(po);
        poFile.addEntry("Keep");
        // "Remove" not encountered

        assertTrue(poFile.hasChanges(), "Should detect removed entry");
    }
}
