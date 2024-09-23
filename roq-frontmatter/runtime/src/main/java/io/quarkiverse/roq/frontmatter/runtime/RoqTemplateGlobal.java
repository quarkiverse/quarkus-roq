package io.quarkiverse.roq.frontmatter.runtime;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class RoqTemplateGlobal {
    static LocalDateTime now = LocalDateTime.now();
    static String roqVersion = Objects.toString(RoqTemplateGlobal.class.getPackage().getImplementationVersion(), "???");
    static String locale = Locale.getDefault().toString();
}