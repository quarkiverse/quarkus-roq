package io.quarkiverse.roq.plugin.toc.runtime;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.qute.TemplateData;

/**
 * Represents a single entry in a table of contents, with support for nested children.
 */

@TemplateData
public class TocEntry {

    private final String id;
    private final String title;
    private final int level;
    private final List<TocEntry> children;

    public TocEntry(String id, String title, int level) {
        this.id = id;
        this.title = title;
        this.level = level;
        this.children = new ArrayList<>();
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int level() {
        return level;
    }

    public List<TocEntry> children() {
        return children;
    }
}
