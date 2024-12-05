package io.quarkiverse.roq.plugin.series.runtime;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "series", defaultKey = Message.UNDERSCORED_ELEMENT_NAME)
public interface SeriesMessage {

    @Message
    String header(String title, Integer count);

}
