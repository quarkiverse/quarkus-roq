package io.quarkiverse.roq.exception;

/**
 * Base exception for Roq errors carrying structured information
 * for rendering friendly error pages.
 */
public class RoqException extends RuntimeException {

    private final String title;
    private final RoqSourceInfo sourceInfo;
    private final String detail;
    private final String hint;
    private final Throwable originalCause;

    protected RoqException(Builder builder) {
        // Do NOT pass cause to super: Quarkus ErrorPageGenerators uses getRootCause()
        // to match exception class names. If we set a cause, the root cause becomes
        // the wrapped exception (e.g. DateTimeParseException) and our generator is
        // never called. We store it separately for display in the error page.
        super(builder.buildMessage());
        this.title = builder.title;
        this.sourceInfo = builder.buildSourceInfo();
        this.detail = builder.detail;
        this.hint = builder.hint;
        this.originalCause = builder.cause;
    }

    public String title() {
        return title;
    }

    public RoqSourceInfo sourceInfo() {
        return sourceInfo;
    }

    public String detail() {
        return detail;
    }

    public String hint() {
        return hint;
    }

    public Integer line() {
        return sourceInfo != null ? sourceInfo.line() : null;
    }

    public Integer column() {
        return sourceInfo != null ? sourceInfo.column() : null;
    }

    /**
     * Returns the original cause (stored separately to keep this exception as the root cause
     * for Quarkus ErrorPageGenerators matching).
     */
    public Throwable originalCause() {
        return originalCause;
    }

    public String displayPath() {
        return sourceInfo != null ? sourceInfo.relativePath() : null;
    }

    public String absolutePath() {
        return sourceInfo != null ? sourceInfo.absolutePath() : null;
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static class Builder {
        private final String title;
        private String sourceFilePath;
        private String absoluteSourceFilePath;
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

        public Builder absoluteSourceFilePath(String absoluteSourceFilePath) {
            this.absoluteSourceFilePath = absoluteSourceFilePath;
            return this;
        }

        public Builder sourceInfo(RoqSourceInfo sourceInfo) {
            this.sourceFilePath = sourceInfo.relativePath();
            this.absoluteSourceFilePath = sourceInfo.absolutePath();
            this.line = sourceInfo.line();
            this.column = sourceInfo.column();
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

        private RoqSourceInfo buildSourceInfo() {
            if (sourceFilePath == null && absoluteSourceFilePath == null && line == null && column == null) {
                return null;
            }
            return new RoqSourceInfo(sourceFilePath, absoluteSourceFilePath, line, column);
        }

        private String buildMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            String path = sourceFilePath;
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
