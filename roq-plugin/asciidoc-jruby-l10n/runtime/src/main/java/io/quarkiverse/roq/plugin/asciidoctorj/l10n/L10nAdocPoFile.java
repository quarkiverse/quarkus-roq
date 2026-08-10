package io.quarkiverse.roq.plugin.asciidoctorj.l10n;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoWriter;
import org.fedorahosted.tennera.jgettext.catalog.parse.MessageStreamParser;

/**
 * Thread-safe PO file representation.
 * Multiple tree processors may access the same cached instance concurrently.
 */
class L10nAdocPoFile {

    private final Map<String, String> translations;
    private final Map<String, Message> existingMessages;
    private final CopyOnWriteArrayList<String> encounteredMsgids;
    private final AtomicBoolean dirty;

    L10nAdocPoFile(Path poFile) throws IOException {
        Map<String, Message> parsed = parseMessages(poFile.toFile());
        this.existingMessages = new ConcurrentHashMap<>(parsed);
        this.translations = extractTranslations(parsed);
        this.encounteredMsgids = new CopyOnWriteArrayList<>();
        this.dirty = new AtomicBoolean(false);
    }

    L10nAdocPoFile() {
        this.existingMessages = new ConcurrentHashMap<>();
        this.translations = new ConcurrentHashMap<>();
        this.encounteredMsgids = new CopyOnWriteArrayList<>();
        this.dirty = new AtomicBoolean(false);
    }

    String translate(String msgid) {
        return translations.get(msgid);
    }

    /**
     * Adds an entry to be tracked in this PO file.
     * Thread-safe: uses concurrent collections for lock-free operation.
     */
    void addEntry(String msgid) {
        if (msgid == null || msgid.isBlank()) {
            return;
        }
        encounteredMsgids.addIfAbsent(msgid);

        existingMessages.computeIfAbsent(msgid, key -> {
            dirty.set(true);
            Message msg = new Message();
            msg.setMsgid(key);
            msg.setMsgstr("");
            return msg;
        });
    }

    /**
     * Checks if there are unsaved changes to this PO file.
     * Thread-safe: reads from concurrent collections.
     */
    boolean hasChanges() {
        if (dirty.get()) {
            return true;
        }
        long existingNonHeaderCount = existingMessages.keySet().stream()
                .filter(k -> !k.isEmpty())
                .count();
        return existingNonHeaderCount != encounteredMsgids.size();
    }

    /**
     * Writes this PO file to disk.
     * Synchronized to ensure atomic: snapshot + write + cleanup.
     * While individual collections are thread-safe, we need a consistent
     * snapshot of all state when writing the file and cleaning up afterwards.
     */
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
        existingMessages.putIfAbsent("", header);
        dirty.set(false);
    }

    private Map<String, Message> parseMessages(File file) throws IOException {
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

    private Map<String, String> extractTranslations(Map<String, Message> messages) {
        // Use ConcurrentHashMap since translations may be read concurrently
        Map<String, String> result = new ConcurrentHashMap<>();
        for (Message msg : messages.values()) {
            String msgid = msg.getMsgid();
            String msgstr = msg.getMsgstr();
            if (msgid == null || msgid.isBlank()) {
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
