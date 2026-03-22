package io.quarkiverse.roq.editor.runtime.devui;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Roq editor extension.
 * <p>
 * NOTE: Prior versions used the {@code editor.*} configuration prefix. This has been
 * changed to {@code quarkus.roq.editor.*}. Existing configurations must be migrated
 * by renaming keys from {@code editor.*} to {@code quarkus.roq.editor.*}, otherwise
 * they will no longer be read.
 */
@ConfigMapping(prefix = "quarkus.roq.editor")
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

    /**
     * Git sync configuration
     */
    @JsonProperty("sync")
    SyncConfig sync();

    interface SyncConfig {

        /**
         * Enable Git sync feature (commit, push, pull via the Editor UI)
         */
        @JsonProperty("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * Auto-sync configuration (pull from remote)
         */
        @JsonProperty("autoSync")
        AutoSyncConfig autoSync();

        interface AutoSyncConfig {
            /**
             * Enable automatic sync (pull) from remote
             */
            @JsonProperty("enabled")
            @WithDefault("false")
            boolean enabled();

            /**
             * Auto-sync interval in seconds
             */
            @JsonProperty("intervalSeconds")
            @WithDefault("60")
            int intervalSeconds();
        }

        /**
         * Auto-publish configuration (commit + push)
         */
        @JsonProperty("autoPublish")
        AutoPublishConfig autoPublish();

        interface AutoPublishConfig {
            /**
             * Enable automatic publish (commit + push) on content changes
             */
            @JsonProperty("enabled")
            @WithDefault("false")
            boolean enabled();

            /**
             * Auto-publish interval in seconds
             */
            @JsonProperty("intervalSeconds")
            @WithDefault("300")
            int intervalSeconds();
        }

        /**
         * Commit message configuration
         */
        @JsonProperty("commitMessage")
        CommitMessageConfig commitMessage();

        interface CommitMessageConfig {
            /**
             * Default commit message template
             */
            @JsonProperty("template")
            @WithDefault("Update content via Roq Editor")
            String template();
        }
    }

}
