package io.quarkiverse.roq.editor.runtime.devui;

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
    @WithDefault("markdown")
    Markup pageMarkup();

    /**
     * Markup to use for new docs
     */
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
    VisualEditorConfig visualEditor();

    interface VisualEditorConfig {

        /**
         * When true, use the visual editor on supported files (Markdown).
         * When false, always use the simple editor
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Use simple editor if the file contains qute or html blocks without data-type="raw" to make sure we don't break
         * existing content
         */
        @WithDefault("true")
        boolean safe();
    }

    /**
     * Suggested path configuration
     */
    SuggestedPathConfig suggestedPath();

    interface SuggestedPathConfig {

        /**
         * If enabled, Editor will suggest file path sync when it differs from content.
         */
        @WithDefault("true")
        boolean enabled();

    }

    /**
     * Git sync configuration
     */
    SyncConfig sync();

    interface SyncConfig {

        /**
         * Enable Git sync feature (commit, push, pull via the Editor UI)
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Auto-sync configuration (pull from remote)
         */
        AutoSyncConfig autoSync();

        interface AutoSyncConfig {
            /**
             * Enable automatic sync (pull) from remote
             */
            @WithDefault("false")
            boolean enabled();

            /**
             * Auto-sync interval in seconds
             */
            @WithDefault("60")
            int intervalSeconds();
        }

        /**
         * Auto-publish configuration (commit + push)
         */
        AutoPublishConfig autoPublish();

        interface AutoPublishConfig {
            /**
             * Enable automatic publish (commit + push) on content changes
             */
            @WithDefault("false")
            boolean enabled();

            /**
             * Auto-publish interval in seconds
             */
            @WithDefault("300")
            int intervalSeconds();
        }

        /**
         * Commit message configuration
         */
        CommitMessageConfig commitMessage();

        interface CommitMessageConfig {
            /**
             * Default commit message template
             */
            @WithDefault("Update content via Roq Editor")
            String template();
        }
    }

}
