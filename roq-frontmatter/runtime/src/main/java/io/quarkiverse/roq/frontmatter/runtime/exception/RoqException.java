package io.quarkiverse.roq.frontmatter.runtime.exception;

import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;

/**
 * Base exception for Roq errors carrying structured information
 * for rendering friendly error pages.
 */
public class RoqException extends RuntimeException {

    private final String title;
    private final String sourceFilePath;
    private final TemplateSource source;
    private final String detail;
    private final String hint;
    private final Integer line;
    private final Integer column;
    private final Throwable originalCause;

    protected RoqException(Builder builder) {
        // Do NOT pass cause to super: Quarkus ErrorPageGenerators uses getRootCause()
        // to match exception class names. If we set a cause, the root cause becomes
        // the wrapped exception (e.g. DateTimeParseException) and our generator is
        // never called. We store it separately for display in the error page.
        super(builder.buildMessage());
        this.title = builder.title;
        this.sourceFilePath = builder.sourceFilePath;
        this.source = builder.source;
        this.detail = builder.detail;
        this.hint = builder.hint;
        this.line = builder.line;
        this.column = builder.column;
        this.originalCause = builder.cause;
    }

    public String title() {
        return title;
    }

    public String sourceFilePath() {
        return sourceFilePath;
    }

    /**
     * The template source associated with this error, if available.
     */
    public TemplateSource source() {
        return source;
    }

    public String detail() {
        return detail;
    }

    public String hint() {
        return hint;
    }

    public Integer line() {
        return line;
    }

    public Integer column() {
        return column;
    }

    /**
     * Returns the original cause (stored separately to keep this exception as the root cause
     * for Quarkus ErrorPageGenerators matching).
     */
    public Throwable originalCause() {
        return originalCause;
    }

    /**
     * Returns the best available relative path for display.
     * Prefers {@code source.file().relativePath()} when a source is set,
     * falls back to {@code sourceFilePath}.
     */
    public String displayPath() {
        if (source != null && source.file() != null) {
            return source.file().relativePath();
        }
        return sourceFilePath;
    }

    /**
     * Returns the full absolute path when available (for tooltips).
     * Returns null if no absolute path can be determined.
     */
    public String absolutePath() {
        if (source != null && source.file() != null) {
            return source.file().absolutePath();
        }
        return null;
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static class Builder {
        private final String title;
        private String sourceFilePath;
        private TemplateSource source;
        private String detail;
        private String hint;
        private Integer line;
        private Integer column;
        private Throwable cause;

        private Builder(String title) {
            this.title = title;
        }

        public Builder sourceFilePath(String sourceFilePath) {
            this.sourceFilePath = sourceFilePath;
            return this;
        }

        /**
         * Set the template source for this error. When set, the source's
         * file paths are used for display (relative) and tooltip (absolute).
         */
        public Builder source(TemplateSource source) {
            this.source = source;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder hint(String hint) {
            this.hint = hint;
            return this;
        }

        public Builder line(Integer line) {
            this.line = line;
            return this;
        }

        public Builder column(Integer column) {
            this.column = column;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        private String buildMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            String path = source != null && source.file() != null ? source.file().relativePath() : sourceFilePath;
            if (path != null) {
                sb.append(" [").append(path);
                if (line != null) {
                    sb.append(":").append(line);
                    if (column != null) {
                        sb.append(":").append(column);
                    }
                }
                sb.append("]");
            }
            if (detail != null) {
                sb.append(": ").append(detail);
            }
            if (hint != null) {
                sb.append(" (").append(hint).append(")");
            }
            return sb.toString();
        }

    }
}
