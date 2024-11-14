package io.quarkiverse.roq.plugin.qrcode.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionHelperFactory.BlockInfo;
import io.quarkus.qute.SectionHelperFactory.SectionInitContext;
import io.quarkus.qute.TemplateException;

public class TypeUtil {

    /**
     * Collects expressions for the given parameter names from the section context.
     *
     * @param context The section initialization context
     * @param names The parameter names to collect
     * @return Map of parameter names to their expressions
     * @throws TemplateException if unexpected parameters are found
     */
    public static Map<String, Expression> collectExpressions(SectionInitContext context, String... names) {
        Map<String, Expression> ret = new HashMap<>();
        Set<String> expectedParameters = new HashSet<>(context.getParameters().keySet());
        for (String name : names) {
            Expression value = context.getExpression(name);
            if (value != null) {
                ret.put(name, value);
            }
            expectedParameters.remove(name);
        }
        if (!expectedParameters.isEmpty()) {
            throw new TemplateException("Unexpected parameters to template: " + expectedParameters + " (we only know about "
                    + Arrays.toString(names) + ")");
        }
        return ret;
    }

    /**
     * Declares block parameters in the template.
     *
     * @param block The block info
     * @param names The parameter names to declare
     */
    public static void declareBlock(BlockInfo block, String... names) {
        for (String name : names) {
            String value = block.getParameter(name);
            if (value != null) {
                block.addExpression(name, value);
            }
        }
    }

    /**
     * Type checks a value from the parameters map.
     *
     * @param values The parameters map
     * @param name The parameter name
     * @param type The expected type
     * @param <T> The type parameter
     * @return The type-checked value
     */
    public static <T> T typecheckValue(Map<String, Object> values, String name, Class<? extends T> type) {
        return typecheckValue(values, name, type, null);
    }

    /**
     * Type checks a value from the parameters map with a default value.
     *
     * @param values The parameters map
     * @param name The parameter name
     * @param type The expected type
     * @param defaultValue The default value if parameter is missing
     * @param <T> The type parameter
     * @return The type-checked value or default
     * @throws TemplateException if value is of wrong type
     */
    public static <T> T typecheckValue(Map<String, Object> values, String name, Class<? extends T> type, T defaultValue) {
        Object valueObject = values.get(name);
        if (valueObject == null) {
            return defaultValue;
        }
        if (!type.isAssignableFrom(valueObject.getClass()))
            throw new TemplateException("Invalid " + name + " parameter: " + valueObject + " should be of type " + type
                    + " but is of type " + valueObject.getClass());
        return (T) valueObject;
    }

    /**
     * Checks if a required parameter is present.
     *
     * @param context The section initialization context
     * @param name The parameter name to check
     * @throws IllegalStateException if parameter is missing
     */
    public static void requireParameter(SectionInitContext context, String name) {
        if (context.getParameter(name) == null) {
            throw new IllegalStateException("Missing parameter: " + name);
        }
    }
}
