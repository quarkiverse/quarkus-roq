package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoWriter;
import org.fedorahosted.tennera.jgettext.catalog.parse.MessageStreamParser;

class L10nAdocPoFile {

    private final Map<String, String> translations;
    private final Map<String, Message> existingMessages;
    private final List<String> encounteredMsgids = new ArrayList<>();
    private boolean dirty;

    L10nAdocPoFile(Path poFile) throws IOException {
        this.existingMessages = parseMessages(poFile.toFile());
        this.translations = extractTranslations(existingMessages);
    }

    L10nAdocPoFile() {
        this.existingMessages = new LinkedHashMap<>();
        this.translations = new HashMap<>();
    }

    String translate(String msgid) {
        return translations.get(msgid);
    }

    synchronized void addEntry(String msgid) {
        if (msgid == null || msgid.isBlank()) {
            return;
        }
        if (!encounteredMsgids.contains(msgid)) {
            encounteredMsgids.add(msgid);
        }
        if (!existingMessages.containsKey(msgid)) {
            Message msg = new Message();
            msg.setMsgid(msgid);
            msg.setMsgstr("");
            existingMessages.put(msgid, msg);
            dirty = true;
        }
    }

    synchronized boolean hasChanges() {
        if (dirty) {
            return true;
        }
        long existingNonHeaderCount = existingMessages.keySet().stream()
                .filter(k -> !k.isEmpty())
                .count();
        return existingNonHeaderCount != encounteredMsgids.size();
    }

    synchronized void writeTo(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Catalog catalog = new Catalog();

        Message header = existingMessages.get("");
        if (header == null) {
            header = new Message();
            header.setMsgid("");
            String language = L10nAdocRecorder.getTargetLanguage();
            StringBuilder headerStr = new StringBuilder();
            if (language != null) {
                headerStr.append("Language: ").append(language).append("\n");
            }
            headerStr.append("MIME-Version: 1.0\n");
            headerStr.append("Content-Type: text/plain; charset=UTF-8\n");
            headerStr.append("Content-Transfer-Encoding: 8bit\n");
            headerStr.append("X-Generator: roq-plugin-l10n-adoc\n");
            header.setMsgstr(headerStr.toString());
        }
        catalog.addMessage(header);

        for (String msgid : encounteredMsgids) {
            Message msg = existingMessages.get(msgid);
            if (msg != null) {
                catalog.addMessage(msg);
            }
        }

        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            new PoWriter().write(catalog, bw);
        }
        // Reset change tracking — the file now matches the in-memory state
        existingMessages.keySet().retainAll(encounteredMsgids);
        existingMessages.putIfAbsent("", new Message());
        dirty = false;
    }

    private static Map<String, Message> parseMessages(File file) throws IOException {
        Map<String, Message> result = new LinkedHashMap<>();
        MessageStreamParser parser = new MessageStreamParser(file);
        while (parser.hasNext()) {
            Message msg = parser.next();
            if (msg.getMsgid() != null) {
                result.put(msg.getMsgid(), msg);
            }
        }
        return result;
    }

    private static Map<String, String> extractTranslations(Map<String, Message> messages) {
        Map<String, String> result = new HashMap<>();
        for (Message msg : messages.values()) {
            String msgid = msg.getMsgid();
            String msgstr = msg.getMsgstr();
            if (msgid == null || msgid.isEmpty()) {
                continue;
            }
            if (msgstr == null || msgstr.isEmpty()) {
                continue;
            }
            if (msg.isFuzzy()) {
                continue;
            }
            result.put(msgid, msgstr);
        }
        return result;
    }
}
