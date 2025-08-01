package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.TemplateContext.streamFilter;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterHeaderParserBuildItem extends MultiBuildItem implements Predicate<TemplateContext> {

    public static final int FRONTMATTER_HEADER_PARSER_PRIORITY = 10;

    private final Predicate<TemplateContext> isApplicable;
    private final Function<TemplateContext, JsonObject> parse;
    private final Function<String, String> removeHeader;
    /**
     * The parser with the highest priority will have priority over the data from other parsers
     */
    private final int priority;

    public RoqFrontMatterHeaderParserBuildItem(Predicate<TemplateContext> isApplicable,
            Function<TemplateContext, JsonObject> parse, Function<String, String> removeHeader, int priority) {
        this.isApplicable = isApplicable;
        this.parse = parse;
        this.removeHeader = removeHeader;
        this.priority = priority;
    }

    public Predicate<TemplateContext> isApplicable() {
        return isApplicable;
    }

    public Function<TemplateContext, JsonObject> parse() {
        return parse;
    }

    public Function<String, String> removeHeader() {
        return removeHeader;
    }

    public int priority() {
        return priority;
    }

    @Override
    public boolean test(TemplateContext templateContext) {
        return isApplicable.test(templateContext);
    }

    public static List<RoqFrontMatterHeaderParserBuildItem> resolveHeaderParsers(
            List<RoqFrontMatterHeaderParserBuildItem> items,
            TemplateContext context) {
        return streamFilter(items, context)
                .sorted(Comparator.comparingInt(RoqFrontMatterHeaderParserBuildItem::priority)).toList();
    }

}
