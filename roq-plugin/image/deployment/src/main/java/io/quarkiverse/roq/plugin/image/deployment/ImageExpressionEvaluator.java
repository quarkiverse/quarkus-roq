package io.quarkiverse.roq.plugin.image.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.plugin.image.runtime.RoqImageException;
import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateException;
import io.quarkus.qute.runtime.jsonobject.JsonObjectValueResolver;

/**
 * Evaluates a template expression against the site model at build time, with a plain Qute engine: no CDI, so
 * only reflection and JSON data are available.
 */
final class ImageExpressionEvaluator {

    sealed interface Result permits Value, Empty, Failed, Error {
    }

    /** The expression produced a non blank string. */
    record Value(String value) implements Result {
    }

    /** The expression produced null or a blank string: there is no image. */
    record Empty() implements Result {
    }

    /** The expression does not apply to this context (a property the data does not have, for instance). */
    record Failed(String message) implements Result {
    }

    /** The model rejected the expression: the page declares an image it does not have, for instance. */
    record Error(RoqException error) implements Result {
    }

    private final Engine engine = Engine.builder().addDefaults()
            .addValueResolver(new ReflectionValueResolver())
            .addValueResolver(new JsonObjectValueResolver())
            .strictRendering(true)
            .build();
    private final Map<String, Template> templates = new HashMap<>();

    Result evaluate(String expression, Map<String, Object> data) {
        Template template = templates.computeIfAbsent(expression, e -> engine.parse("{" + e + "}"));
        try {
            String value = template.data(data).render();
            return value.isBlank() ? new Empty() : new Value(value);
        } catch (TemplateException e) {
            return new Failed(e.getMessage());
        } catch (RuntimeException e) {
            return new Error(roqError(e));
        }
    }

    /**
     * Qute's reflection resolver wraps whatever a getter throws, so the Roq error is looked for down the chain;
     * anything else becomes one.
     */
    private static RoqException roqError(RuntimeException e) {
        Throwable root = e;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RoqException roq) {
                return roq;
            }
            root = cause;
        }
        return new RoqImageException(RoqException.builder("Image expression evaluation failed")
                .detail(String.valueOf(root.getMessage())).cause(e));
    }
}
