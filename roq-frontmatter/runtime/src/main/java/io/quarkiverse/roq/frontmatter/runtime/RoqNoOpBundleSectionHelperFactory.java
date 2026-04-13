package io.quarkiverse.roq.frontmatter.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;

/**
 * No-op fallback for the {#bundle /} section tag.
 * When Web Bundler is on the classpath it registers the real tag which takes precedence.
 */
@EngineConfiguration
public class RoqNoOpBundleSectionHelperFactory
        implements SectionHelperFactory<RoqNoOpBundleSectionHelperFactory.NoOpSectionHelper> {

    @Override
    public List<String> getDefaultAliases() {
        return List.of("bundle");
    }

    @Override
    public NoOpSectionHelper initialize(SectionInitContext context) {
        return new NoOpSectionHelper();
    }

    static class NoOpSectionHelper implements SectionHelper {
        @Override
        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
            return ResultNode.NOOP;
        }
    }
}