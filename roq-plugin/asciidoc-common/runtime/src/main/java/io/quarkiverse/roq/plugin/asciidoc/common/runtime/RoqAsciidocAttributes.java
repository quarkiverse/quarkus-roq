package io.quarkiverse.roq.plugin.asciidoc.common.runtime;

/**
 * How the Roq AsciiDoc plugins read the values of {@code quarkus.asciidoc.attributes.*}.
 */
public final class RoqAsciidocAttributes {
    /**
     * Value of a boolean attribute (a flag that is set or unset, such as {@code sectanchors}), since SmallRye Config
     * rejects empty map values.
     */
    public static final String TRUE = Boolean.TRUE.toString();

    /**
     * Marker for a value attribute that must be empty (such as {@code idprefix}), since SmallRye Config rejects empty
     * map values. The Jekyll migration tool writes the same marker from its own copy of this constant.
     */
    // TODO write the marker in the javadoc of AsciidoctorJConfig and AsciidocCommonConfig once
    // https://github.com/quarkusio/quarkus/pull/57909 is released (until then the generated config docs would render
    // <<empty>> as a broken xref)
    public static final String EMPTY = "<<empty>>";

    private RoqAsciidocAttributes() {
    }

    /**
     * The value that the AsciiDoc parser gets for a configured value: {@link #TRUE} and {@link #EMPTY} become an empty
     * value, any other value is kept.
     */
    public static String value(String configured) {
        return TRUE.equals(configured) || EMPTY.equals(configured) ? "" : configured;
    }
}
