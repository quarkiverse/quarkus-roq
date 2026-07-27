package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class L10nAdocRecorder {

    // Stored in a static volatile field so it can be read by L10nAdocExtensionRegistry,
    // which is loaded via ServiceLoader (outside Quarkus CDI) at Asciidoctor render time.
    private static volatile Path poBaseDir;
    private static volatile boolean extractOnBuild = true;
    private static volatile String targetLanguage;

    public void setPoBaseDir(Optional<String> dir) {
        poBaseDir = dir.map(Path::of).orElse(null);
    }

    public void setExtractOnBuild(Optional<Boolean> extract) {
        extractOnBuild = extract.orElse(true);
    }

    public void setTargetLanguage(Optional<String> targetLanguage) {
        L10nAdocRecorder.targetLanguage = targetLanguage.orElse(null);
    }

    public static Path getPoBaseDir() {
        return poBaseDir;
    }

    public static boolean isExtractOnBuild() {
        return extractOnBuild;
    }

    public static String getTargetLanguage() {
        return targetLanguage;
    }
}
