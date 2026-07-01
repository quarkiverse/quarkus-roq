package io.quarkiverse.roq.plugin.image.runtime;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageImageCustomizer;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;

/**
 * When the image plugin is present, {@link Page#image(Object)} and {@link Site#image(Object)}
 * return the raw image name so the {@code {#image}} tag can look it up in the image processor's index.
 */
@Singleton
public class RoqImageCustomizer implements PageImageCustomizer {

    @Override
    public RoqUrl resolveImage(Page page, String name) {
        return RoqUrl.fromRoot(null, name);
    }

    @Override
    public RoqUrl resolveImage(Site site, String name) {
        return RoqUrl.fromRoot(null, name);
    }
}
