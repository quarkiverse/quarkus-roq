package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.nio.file.Path;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

public class L10nAdocExtensionRegistry implements ExtensionRegistry {

    @Override
    public void register(Asciidoctor asciidoctor) {
        Path poBaseDir = resolvePoBaseDir();
        asciidoctor.javaExtensionRegistry()
                .preprocessor(new L10nAdocPreprocessor(poBaseDir))
                .treeprocessor(new L10nAdocTreeprocessor(poBaseDir));
    }

    static Path resolvePoBaseDir() {
        Path fromRecorder = L10nAdocRecorder.getPoBaseDir();
        if (fromRecorder != null) {
            return fromRecorder;
        }
        String env = System.getenv("L10N_PO_BASE_DIR");
        return env != null ? Path.of(env) : null;
    }
}
