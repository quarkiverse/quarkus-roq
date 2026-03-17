package io.quarkiverse.roq.theme;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "roq_theme", locale = "en")
public interface ThemeMessageBundle {

    @Message(defaultValue = "Contact me")
    String contact_title();

    @Message(defaultValue = "Roq! Where's my page?")
    String notfound_title();

    @Message(defaultValue = "If you are lost, find yourself")
    String notfound_text();

    @Message(defaultValue = "here")
    String notfound_link();

}
