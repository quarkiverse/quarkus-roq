package io.quarkiverse.roq.migration.gatsby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class GatsbyConfigTest {

    // Shaped like the Quarkiverse Hub's gatsby-config.js
    static final String CONFIG = """
            /**
             * See: https://www.gatsbyjs.com/docs/reference/config-files/gatsby-config/
             */
            module.exports = {
                siteMetadata: {
                    title: `Quarkiverse Hub`,
                    description: `The Quarkiverse Hub provides support for Quarkus extension projects.`,
                    siteUrl: `https://hub.quarkiverse.io`,
                },
                plugins: [
                    {
                        resolve: `gatsby-source-filesystem`,
                        options: {
                            name: `content`,
                            path: `${__dirname}/../docs`,
                        },
                    }, {
                        resolve: `gatsby-transformer-remark`,
                        options: {
                            plugins: [
                                `gatsby-remark-autolink-headers`,
                                `gatsby-remark-emoji`,
                            ]
                        },
                    },
                    "gatsby-frontmatter-defaults",
                    `gatsby-plugin-image`,
                    {
                        resolve: `gatsby-source-filesystem`,
                        options: {
                            name: `images`,
                            path: `${__dirname}/src/images`,
                        },
                    },
                    {
                        resolve: `gatsby-plugin-manifest`,
                        options: {
                            name: `gatsby-starter-default`,
                            // https://css-tricks.com/meta-theme-color-and-trickery/
                            icon: `src/images/logo.png`,
                        },
                    },
                ],
            }
            """;

    @Test
    void readsTheStringOptionsOfThePlugins() {
        assertThat(GatsbyConfig.parse(CONFIG).pluginOptions().get("gatsby-plugin-manifest"))
                .containsExactly(Map.entry("name", "gatsby-starter-default"), Map.entry("icon", "src/images/logo.png"));
        // The first of the filesystem sources
        assertThat(GatsbyConfig.parse(CONFIG).pluginOptions().get("gatsby-source-filesystem")).containsKey("path");
    }

    @Test
    void readsSiteMetadata() {
        assertThat(GatsbyConfig.parse(CONFIG).siteMetadata())
                .containsEntry("title", "Quarkiverse Hub")
                .containsEntry("siteUrl", "https://hub.quarkiverse.io")
                .containsKey("description");
    }

    @Test
    void readsPluginsIncludingNestedOnes() {
        assertThat(GatsbyConfig.parse(CONFIG).plugins()).containsExactly(
                "gatsby-source-filesystem", "gatsby-transformer-remark", "gatsby-remark-autolink-headers",
                "gatsby-remark-emoji", "gatsby-frontmatter-defaults", "gatsby-plugin-image", "gatsby-plugin-manifest");
    }

    @Test
    void readsFilesystemSourcesRelativeToTheProject() {
        assertThat(GatsbyConfig.parse(CONFIG).sources()).containsExactly(
                new GatsbyConfig.Source("content", "../docs"),
                new GatsbyConfig.Source("images", "src/images"));
    }

    @Test
    void readsAnEmptyConfig() {
        GatsbyConfig config = GatsbyConfig.parse("module.exports = {}");

        assertThat(config.siteMetadata()).isEmpty();
        assertThat(config.plugins()).isEmpty();
        assertThat(config.sources()).isEmpty();
    }
}
