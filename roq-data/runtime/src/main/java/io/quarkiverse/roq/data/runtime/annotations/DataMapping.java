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
     * Indicates the name of the data file, excluding the file extension.
     * This name should match the data source file used for mapping purposes.
     *
     * @return the data file name without its extension
     */
    String value();

    /**
     * Indicates if the root element of the data file is structured as an array.
     * Set this to {@code true} if the root element is an array; otherwise, {@code false}.
     *
     * @return {@code true} if the root element is an array, {@code false} otherwise
     */
    boolean parentArray() default false;

    /**
     * Defines whether the corresponding data file is required.
     *
     * @return {@code true} if the data file is mandatory, {@code false} if it's optional.
     */
    boolean required() default false;
}
