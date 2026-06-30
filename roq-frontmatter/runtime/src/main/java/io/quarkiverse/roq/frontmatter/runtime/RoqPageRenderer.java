package io.quarkiverse.roq.frontmatter.runtime;

import java.util.concurrent.CompletionStage;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.qute.Template;

public interface RoqPageRenderer {

    CompletionStage<String> render(Page page, Template template, String locale);
}
