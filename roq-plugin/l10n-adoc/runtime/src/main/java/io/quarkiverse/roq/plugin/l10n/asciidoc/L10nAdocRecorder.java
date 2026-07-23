package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class L10nAdocRecorder {

    // Stored in a static volatile field so it can be read by L10nAdocExtensionRegistry,
    // which is loaded via ServiceLoader (outside Quarkus CDI) at Asciidoctor render time.
    private static volatile Path poBaseDir;

    public void setPoBaseDir(Optional<String> dir) {
        poBaseDir = dir.map(Path::of).orElse(null);
    }

    public static Path getPoBaseDir() {
        return poBaseDir;
    }
}
