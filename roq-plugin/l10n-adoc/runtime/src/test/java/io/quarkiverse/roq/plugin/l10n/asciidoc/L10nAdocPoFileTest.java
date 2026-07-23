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
}
