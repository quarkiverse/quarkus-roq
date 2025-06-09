package io.quarkiverse.roq.plugin.diagram.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionHelperFactory.BlockInfo;
import io.quarkus.qute.SectionHelperFactory.SectionInitContext;
import io.quarkus.qute.TemplateException;

/**
 * Copy/pasted from qrcode plugin. Maybe this should be mooved to a common place in the future.
 */
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
}
