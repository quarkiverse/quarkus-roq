package io.quarkiverse.roq.plugin.series.runtime;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "series", defaultKey = Message.UNDERSCORED_ELEMENT_NAME, locale = "en")
public interface SeriesMessage {

    @Message(defaultValue = "{title} ({count} Parts Series)")
    String header(String title, Integer count);

}
