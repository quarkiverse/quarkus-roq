package io.quarkiverse.roq.editor.runtime.devui;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "editor")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqEditorConfig {

    /**
     * Markup to use for new pages
     */
    @JsonProperty("pageMarkup")
    @WithDefault("markdown")
    Markup pageMarkup();

    /**
     * Markup to use for new docs
     */
    @JsonProperty("docMarkup")
    @WithDefault("markdown")
    Markup docMarkup();

    enum Markup {
        MARKDOWN,
        ASCIIDOC,
        HTML
    }

    /**
     * Visual Editor configuration
     */
    @JsonProperty("visualEditor")
    VisualEditorConfig visualEditor();

    interface VisualEditorConfig {

        /**
         * When true, use the visual editor on supported files (Markdown).
         * When false, always use the simple editor
         */
        @JsonProperty("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * Use simple editor if the file contains qute or html blocks without data-type="raw" to make sure we don't break
         * existing content
         */
        @JsonProperty("safe")
        @WithDefault("true")
        boolean safe();
    }

    /**
     * Suggested path configuration
     *
     * @return
     */
    @JsonProperty("suggestedPath")
    SuggestedPathConfig suggestedPath();

    interface SuggestedPathConfig {

        /**
         * If enabled, Editor will suggest file path sync when it differs from content.
         */
        @JsonProperty("enabled")
        @WithDefault("true")
        boolean enabled();

    }

}
