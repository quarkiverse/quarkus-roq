package io.quarkiverse.roq.theme.antoroq;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "roq_theme", locale = "en")
public interface ThemeMessageBundle {

    @Message(defaultValue = "Untitled")
    String defaultPageTitle();

    @Message(defaultValue = "Home")
    String home();

    @Message(defaultValue = "Edit this page")
    String editThisPage();

    @Message(defaultValue = "Previous")
    String previous();

    @Message(defaultValue = "Next")
    String next();

    @Message(defaultValue = "No versions")
    String noVersions();

    @Message(defaultValue = "On this page")
    String onThisPage();

    @Message(defaultValue = "Table of contents")
    String tableOfContents();

    @Message(defaultValue = "Contact")
    String contact_title();

}
