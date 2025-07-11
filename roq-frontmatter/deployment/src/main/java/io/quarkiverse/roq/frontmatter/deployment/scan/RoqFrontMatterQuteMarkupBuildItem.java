package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterQuteMarkupBuildItem extends MultiBuildItem implements Predicate<TemplateContext> {
    private final String name;
    private final Predicate<TemplateContext> isApplicable;
    private final QuteMarkupSection markupSection;

    public RoqFrontMatterQuteMarkupBuildItem(String name, Predicate<TemplateContext> isApplicable,
            QuteMarkupSection markupSection) {
        this.name = name;
        this.isApplicable = isApplicable;
        this.markupSection = markupSection;
    }

    public String name() {
        return name;
    }

    public Predicate<TemplateContext> isApplicable() {
        return isApplicable;
    }

    public QuteMarkupSection markupSection() {
        return markupSection;
    }

    @Override
    public boolean test(TemplateContext context) {
        return isApplicable.test(context);
    }

    public static WrapperFilter findMarkupFilter(List<RoqFrontMatterQuteMarkupBuildItem> markupList, TemplateContext context) {
        if (context.getExtension() == null) {
            return null;
        }
        final List<RoqFrontMatterQuteMarkupBuildItem> applicable = TemplateContext.streamFilter(markupList, context).toList();
        if (applicable.size() > 1) {
            final String markups = markupList.stream().map(RoqFrontMatterQuteMarkupBuildItem::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Multiple applicable markups found (%s) for template '%s': ".formatted(markups, context.templatePath()));
        }
        if (applicable.isEmpty()) {
            return null;
        }
        return applicable.get(0).toWrapperFilter();
    }

    public WrapperFilter toWrapperFilter() {
        return new WrapperFilter(this.markupSection.open + "\n", "\n" + this.markupSection.close);
    }

    public record QuteMarkupSection(String open, String close) {

        public static WrapperFilter find(Map<String, WrapperFilter> markups, String fileName,
                WrapperFilter defaultFilter) {
            final String extension = PathUtils.getExtension(fileName);
            if (extension == null) {
                return defaultFilter;
            }
            if (!markups.containsKey(extension)) {
                return defaultFilter;
            }
            return markups.get(extension);
        }
    }

    public static record WrapperFilter(String prefix, String suffix) implements Function<String, String> {

        public static final WrapperFilter EMPTY = new WrapperFilter("", "");

        @Override
        public String apply(String s) {
            if (EMPTY.equals(this)) {
                return s;
            }
            return prefix() + s + suffix();
        }
    }
}
