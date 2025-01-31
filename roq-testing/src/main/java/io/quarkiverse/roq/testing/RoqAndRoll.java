package io.quarkiverse.roq.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // Annotation is retained at runtime for test setup.
@Target(ElementType.TYPE) // Applied only at the class level.
public @interface RoqAndRoll {

    int port() default 8082;

}
