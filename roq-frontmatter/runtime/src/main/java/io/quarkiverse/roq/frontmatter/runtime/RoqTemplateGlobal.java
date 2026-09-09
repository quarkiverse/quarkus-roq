package io.quarkiverse.roq.frontmatter.runtime;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class RoqTemplateGlobal {
    static LocalDateTime now = LocalDateTime.now();
    static String roqVersion = Objects.toString(RoqTemplateGlobal.class.getPackage().getImplementationVersion(), "???");
    // Locale#toString() (e.g. "en_US") matches the Open Graph og:locale convention, used as-is for that tag.
    static String locale = Locale.getDefault().toString();
    // HTML's lang attribute requires a BCP 47 tag (e.g. "en-US"), hence the separate toLanguageTag() global.
    static String htmlLocale = Locale.getDefault().toLanguageTag();
}