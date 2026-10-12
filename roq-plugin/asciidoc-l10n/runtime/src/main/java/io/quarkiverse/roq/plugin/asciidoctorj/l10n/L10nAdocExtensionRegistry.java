package io.quarkiverse.roq.plugin.asciidoctorj.l10n;

import java.nio.file.Path;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

public class L10nAdocExtensionRegistry implements ExtensionRegistry {

    @Override
    public void register(Asciidoctor asciidoctor) {
        Path poBaseDir = L10nAdocRecorder.getPoBaseDir();
        boolean extractOnBuild = L10nAdocRecorder.isExtractOnBuild();
        asciidoctor.javaExtensionRegistry()
                .preprocessor(new L10nAdocPreprocessor(poBaseDir))
                .treeprocessor(new L10nAdocTreeprocessor(poBaseDir, extractOnBuild));
    }
}
