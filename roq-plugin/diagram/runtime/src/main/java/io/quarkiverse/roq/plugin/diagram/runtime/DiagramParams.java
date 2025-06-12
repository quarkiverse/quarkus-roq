package io.quarkiverse.roq.plugin.diagram.runtime;

import java.util.Objects;

import io.quarkus.qute.TemplateException;

public record DiagramParams(String language, String alt, Integer width, Integer height,
        DiagramConverter.DiagramOutputFormat diagramOutputFormat,
        Boolean asciidoc) {
    public DiagramParams {
        Objects.requireNonNull(language, "property :language is required");
        Objects.requireNonNull(alt, "property :alt is required");
        Objects.requireNonNull(width, "property :width is required");
        Objects.requireNonNull(height, "property :height is required");
        Objects.requireNonNull(diagramOutputFormat, "property :diagramOutputFormat is required");
        Objects.requireNonNull(asciidoc, "property :asciidoc is required");
    }

    public static final class Builder {
        private String language;

        private String alt;

        private Integer width;

        private Integer height;

        private DiagramConverter.DiagramOutputFormat diagramOutputFormat;

        private Boolean asciidoc;

        public Builder() {
        }

        public Builder setLanguage(Object language) {
            this.language = Objects.requireNonNull(typecheckValue(language, "language", String.class),
                    "Null language");
            return this;
        }

        public Builder setAlt(Object alt) {
            this.alt = Objects.requireNonNullElse(typecheckValue(alt, "alt", String.class), "Diagram");
            return this;
        }

        public Builder setWidth(Object width) {
            this.width = Objects.requireNonNullElse(typecheckValue(width, "width", Integer.class), 200);
            return this;
        }

        public Builder setHeight(Object height) {
            this.height = Objects.requireNonNullElse(typecheckValue(height, "height", Integer.class), 200);
            return this;
        }

        public Builder setDiagramOutputFormat(Object diagramOutputFormat) {
            diagramOutputFormat = Objects.requireNonNullElse(diagramOutputFormat, "svg");
            if (!(diagramOutputFormat instanceof String)) {
                throw new TemplateException(
                        "Invalid diagramOutputFormat parameter: " + diagramOutputFormat + " should be of type String");
            }
            this.diagramOutputFormat = DiagramConverter.DiagramOutputFormat.valueOf((String) diagramOutputFormat);
            return this;
        }

        public Builder setAsciidoc(Object asciidoc) {
            this.asciidoc = Objects.requireNonNullElse(typecheckValue(asciidoc, "asciidoc", Boolean.class), false);
            return this;
        }

        public DiagramParams build() {
            if (this.language == null) {
                StringBuilder missing = new StringBuilder();
                if (this.language == null) {
                    missing.append(" language");
                }
                if (this.alt == null) {
                    missing.append(" alt");
                }
                if (this.width == null) {
                    missing.append(" width");
                }
                if (this.height == null) {
                    missing.append(" height");
                }
                if (this.diagramOutputFormat == null) {
                    missing.append(" diagramOutputFormat");
                }
                if (this.asciidoc == null) {
                    missing.append(" asciidoc");
                }
                throw new IllegalStateException("Missing required properties:" + missing);
            }
            return new DiagramParams(this.language, this.alt, this.width, this.height, this.diagramOutputFormat,
                    this.asciidoc);
        }

        public static <T> T typecheckValue(Object value, String name, Class<? extends T> type) {
            if (value == null) {
                return (T) value;
            }
            if (!type.isAssignableFrom(value.getClass()))
                throw new TemplateException("Invalid " + name + " parameter: " + value + " should be of type " + type
                        + " but is of type " + value.getClass());
            return (T) value;
        }
    }
}
