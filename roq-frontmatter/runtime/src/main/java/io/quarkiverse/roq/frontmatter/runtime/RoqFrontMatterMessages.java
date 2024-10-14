package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle(value = "fm", locale = "en")
public interface RoqFrontMatterMessages {

    @Message("Page {index} of {total}")
    public String pageNumber(int index, int total);
}
