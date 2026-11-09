package io.quarkiverse.roq.editor.runtime.devui;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the Roq editor extension.
 */
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
     * Per-collection editor configuration.
     * Keys are collection IDs (e.g., "posts", "docs").
     */
    @JsonIgnore
    @WithName("collections")
    Map<String, CollectionEditorConfig> collectionsMap();

    String DEFAULT_COLLECTION_NAME_PATTERN = ":date-:slug~7";

    interface CollectionEditorConfig {

        /**
         * File name pattern using link-style placeholders.
         * Supported: :date, :slug, :Slug, :name, :Name, :year, :month, :day.
         * Use ~W to truncate to W hyphen-separated words (e.g., :slug~7).
         */
        @WithDefault(DEFAULT_COLLECTION_NAME_PATTERN)
        String name();

        /**
         * If enabled, auto-sync file names when they match the convention and the title/date changes.
         */
        @WithDefault("true")
        boolean syncName();
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
        @WithDefault("false")
        boolean enabled();

        /**
         * Publish workflow mode.
         * <p>
         * {@code PR}: Publish from {@code main} creates a content branch, pushes it and surfaces a
         * PR-creation link. Subsequent publishes on the content branch update the same branch until
         * its remote ref disappears (PR merged), at which point the editor returns to {@code main}.
         * <p>
         * {@code DIRECT}: Publish commits and pushes straight to the current branch.
         */
        @JsonProperty("mode")
        @WithDefault("PR")
        Mode mode();

        enum Mode {
            PR,
            DIRECT
        }

        /**
         * PR-flow specific options. Only consulted when {@link #mode()} is {@code PR}.
         */
        @JsonProperty("prFlow")
        PrFlowConfig prFlow();

        interface PrFlowConfig {

            /**
             * Prefix used for auto-generated content branches. A branch is considered a
             * "content branch" when its name starts with this prefix.
             */
            @JsonProperty("contentBranchPrefix")
            @WithDefault("content/")
            String contentBranchPrefix();

            /**
             * Strategy for subsequent publishes on the same content branch.
             * <p>
             * {@code NEW_COMMITS}: each Publish adds a new commit. No force-push, history preserved.
             * <p>
             * {@code AMEND}: each Publish amends the previous commit. Single-commit PR, requires
             * force-push (with-lease) on the content branch.
             */
            @JsonProperty("commitStrategy")
            @WithDefault("NEW_COMMITS")
            CommitStrategy commitStrategy();

            enum CommitStrategy {
                NEW_COMMITS,
                AMEND
            }

            /**
             * Explicit name of the main/default branch. When empty, it is detected from
             * {@code refs/remotes/origin/HEAD} and falls back to {@code main}.
             */
            @JsonIgnore
            Optional<String> mainBranch();
        }

        /**
         * Optional SSH passphrase used as a fallback when no SSH agent is available to unlock a
         * passphrase-protected key for remote operations.
         * <p>
         * Most users do not need this: JGit uses the system SSH agent (macOS Keychain, {@code ssh-agent},
         * Pageant) automatically. Set it only when no agent is running.
         * <p>
         * For security, provide it via the {@code EDITOR_SYNC_SSH_PASSPHRASE} environment variable or a
         * non-version-controlled config file (e.g. {@code .env}). Never commit it to
         * {@code application.properties}. It is never sent to the browser.
         */
        @JsonIgnore
        Optional<String> sshPassphrase();

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

    /**
     * AI content generation configuration
     */
    @JsonIgnore
    AiConfig ai();

    interface AiConfig {

        /**
         * Custom context to include in every AI content generation request.
         * Use this to set the tone, topic, or style for your blog.
         * For example: "This is a tech blog about Quarkus. Write in a friendly, concise tone."
         */
        @JsonProperty("context")
        Optional<String> context();

    }

}
