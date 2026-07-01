package io.quarkiverse.roq.frontmatter.runtime.model;

/**
 * SPI for plugins to customize how {@link Page#image(Object)} and {@link Site#image(Object)}
 * resolve image paths. When a bean implementing this interface is present, it takes over
 * image resolution (e.g. to return raw names for an image processing pipeline).
 */
public interface PageImageCustomizer {

    RoqUrl resolveImage(Page page, String name);

    RoqUrl resolveImage(Site site, String name);
}
