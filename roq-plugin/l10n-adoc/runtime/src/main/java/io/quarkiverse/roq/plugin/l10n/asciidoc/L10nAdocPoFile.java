package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.catalog.parse.MessageStreamParser;

class L10nAdocPoFile {

    private final Map<String, String> translations;

    L10nAdocPoFile(Path poFile) throws IOException {
        this.translations = parse(poFile.toFile());
    }

    String translate(String msgid) {
        return translations.get(msgid);
    }

    private static Map<String, String> parse(File file) throws IOException {
        Map<String, String> result = new HashMap<>();
        MessageStreamParser parser = new MessageStreamParser(file);
        while (parser.hasNext()) {
            Message msg = parser.next();
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
