package io.quarkiverse.roq.plugin.series.runtime;

/**
 * Frontmatter keys for the Roq Series plugin.
 */
public interface RoqSeriesKeys {
    /**
     * Series identifier — e.g. {@code series: my-tutorial-series}
     * <br>
     * ▸ Scope: document
     * <br>
     * ▸ Access: {@code page.data.getString("series")}
     */
    String SERIES = "series";
}
