package io.quarkiverse.roq.theme.resume;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "roq_theme", locale = "en")
public interface ThemeMessageBundle {

    @Message(defaultValue = "Contact me")
    String contact_title();

    @Message(defaultValue = "Copy link to this section")
    String copy_section_link();

    @Message(defaultValue = "Link copied!")
    String link_copied();

}
