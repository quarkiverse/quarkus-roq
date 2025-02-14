package io.quarkiverse.roq.theme;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "roq_theme", locale = "en")
public interface ThemeMessageBundle {

    @Message(value = "Contact me")
    String contact_title();

}
