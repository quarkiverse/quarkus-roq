package io.quarkiverse.roq.plugin.series.runtime;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "serie", defaultKey = Message.UNDERSCORED_ELEMENT_NAME)
public interface SerieMessage {

    @Message
    String header(String serieName);

    @Message
    String navigate_to();

    @Message
    String select_post();
}
