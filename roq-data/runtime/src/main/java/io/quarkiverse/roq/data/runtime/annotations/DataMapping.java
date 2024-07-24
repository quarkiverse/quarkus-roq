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
    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER })
    @Documented
    @interface ParentArray {
    }
}
