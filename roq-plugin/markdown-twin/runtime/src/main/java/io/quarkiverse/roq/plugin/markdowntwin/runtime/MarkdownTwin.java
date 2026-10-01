package io.quarkiverse.roq.plugin.markdowntwin.runtime;

/**
 * Marker for the Markdown Twin runtime module. The feature has no runtime behaviour of its own: twins are produced at build
 * time as static files (see the deployment processor), so this class only gives the runtime extension jar content.
 */
public final class MarkdownTwin {

    private MarkdownTwin() {
    }
}
