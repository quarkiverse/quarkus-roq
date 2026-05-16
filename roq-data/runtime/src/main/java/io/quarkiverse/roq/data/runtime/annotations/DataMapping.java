package io.quarkiverse.roq.data.runtime.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to indicate that a class is a data mapping.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Documented
public @interface DataMapping {

    /**
     * Indicates the name of the data file (or directory), excluding the file extension.
     * This name should match the data source used for mapping purposes.
     *
     * @return the data file or directory name without its extension
     */
    String value();

    /**
     * The type of data mapping, determining how the data source is read and mapped.
     */
    Type type() default Type.OBJECT_FILE;

    /**
     * Indicates if the root element of the data file is structured as an array.
     * Set this to {@code true} if the root element is an array; otherwise, {@code false}.
     *
     * @return {@code true} if the root element is an array, {@code false} otherwise
     * @deprecated Use {@code type = Type.ARRAY_FILE} instead
     */
    @Deprecated
    boolean parentArray() default false;

    /**
     * Defines whether the corresponding data file is required.
     *
     * @return {@code true} if the data file is mandatory, {@code false} if it's optional.
     */
    boolean required() default false;

    enum Type {
        /**
         * Single data file mapped to a typed object (default).
         */
        OBJECT_FILE,
        /**
         * Single data file with an array root, mapped to a parent type with a {@code List<T>} constructor.
         */
        ARRAY_FILE,
        /**
         * Directory of data files, each mapped to a typed item and collected into a {@code List<T>}.
         * The parent type must have a {@code List<T>} constructor.
         */
        ARRAY_DIR,
        /**
         * Directory of data files, each mapped to a typed item and collected into a {@code Map<String, T>}
         * where keys are filenames (without extension).
         * The parent type must have a {@code Map<String, T>} constructor.
         */
        OBJECT_DIR
    }
}
