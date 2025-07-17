package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.quarkiverse.roq.util.PathUtils;

public record TemplateContext(Path sourceFile, String templatePath, String content) {

    public String getExtension() {
        return PathUtils.getExtension(templatePath);
    }

    public static <T extends Predicate<TemplateContext>> Stream<T> streamFilter(List<T> items, TemplateContext context) {
        return items.stream().filter(i -> i.test(context));
    }

}
