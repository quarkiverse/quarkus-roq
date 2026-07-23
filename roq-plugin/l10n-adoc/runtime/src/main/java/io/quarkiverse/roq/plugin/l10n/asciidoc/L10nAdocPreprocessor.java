package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.nio.file.Path;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Preprocessor;
import org.asciidoctor.extension.PreprocessorReader;

class L10nAdocPreprocessor extends Preprocessor {

    private final Path poBaseDir;

    L10nAdocPreprocessor(Path poBaseDir) {
        this.poBaseDir = poBaseDir;
    }

    @Override
    public PreprocessorReader process(Document document, PreprocessorReader reader) {
        if (poBaseDir != null && !Boolean.TRUE.equals(document.getOptions().get("sourcemap"))) {
            document.setSourcemap(true);
        }
        return reader;
    }
}
