package io.quarkiverse.roq.migration.engine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Static site generators the migration tool can recognise. Recognising a type does not mean it can be
 * migrated: only types with a site migrator can.
 * <p>
 * {@link #detect(Path)} returns the first type, in declaration order, whose rule matches. The order matters where
 * generators share files: Roq comes first because a converted site keeps some of its original files, and Hexo comes
 * before Jekyll because both use {@code _config.yml}.
 */
public enum SiteType {

    ROQ("roq", "Roq", s -> s.contains("pom.xml", "quarkus-roq") || s.contains("build.gradle", "quarkus-roq")
            || s.contains("build.gradle.kts", "quarkus-roq")),
    HEXO("hexo", "Hexo", s -> s.npmDependency("hexo") || (s.isFile("_config.yml") && s.isDirectory("scaffolds"))),
    JEKYLL("jekyll", "Jekyll", s -> s.anyFile("_config.yml", "_config.yaml")
            && (s.isDirectory("_layouts") || s.contains("Gemfile", "jekyll"))),
    MIDDLEMAN("middleman", "Middleman", s -> s.contains("Gemfile", "middleman")),
    HUGO("hugo", "Hugo", s -> s.anyFile("hugo.toml", "hugo.yaml", "hugo.yml", "hugo.json", ".hugo_build.lock")
            || s.isDirectory("archetypes")),
    ZOLA("zola", "Zola", s -> s.contains("config.toml", "base_url") || s.contains("zola.toml", "base_url")),
    ELEVENTY("eleventy", "Eleventy", s -> s.npmDependency("@11ty/eleventy")
            || s.anyFile(".eleventy.js", ".eleventy.cjs", "eleventy.config.js", "eleventy.config.cjs",
                    "eleventy.config.mjs", "eleventy.config.ts")),
    DOCUSAURUS("docusaurus", "Docusaurus", s -> s.npmDependency("@docusaurus/core")
            || s.anyFile("docusaurus.config.js", "docusaurus.config.ts", "docusaurus.config.mjs")),
    GATSBY("gatsby", "Gatsby", s -> s.npmDependency("gatsby")
            || s.anyFile("gatsby-config.js", "gatsby-config.ts", "gatsby-config.mjs")),
    ASTRO("astro", "Astro", s -> s.npmDependency("astro")
            || s.anyFile("astro.config.mjs", "astro.config.js", "astro.config.ts", "astro.config.mts")),
    VITEPRESS("vitepress", "VitePress", s -> s.npmDependency("vitepress")
            || s.isDirectory(".vitepress") || s.isDirectory("docs/.vitepress")),
    VUEPRESS("vuepress", "VuePress", s -> s.npmDependency("vuepress") || s.npmDependency("@vuepress/core")
            || s.isDirectory(".vuepress") || s.isDirectory("docs/.vuepress")),
    // After the other JavaScript generators, some of which are built on Next.js
    NEXTJS("nextjs", "Next.js", s -> s.npmDependency("next")
            || s.anyFile("next.config.js", "next.config.mjs", "next.config.ts")),
    MKDOCS("mkdocs", "MkDocs", s -> s.anyFile("mkdocs.yml", "mkdocs.yaml")),
    SPHINX("sphinx", "Sphinx", s -> s.anyFile("conf.py", "docs/conf.py", "source/conf.py")
            && s.anyFile("index.rst", "docs/index.rst", "source/index.rst")),
    ANTORA("antora", "Antora", s -> s.anyFile("antora-playbook.yml", "antora-playbook.yaml", "antora.yml",
            "docs/antora.yml")),
    JBAKE("jbake", "JBake", s -> s.isFile("jbake.properties")),
    PELICAN("pelican", "Pelican", s -> s.isFile("pelicanconf.py"));

    private final String id;
    private final String displayName;
    private final Predicate<SourceSite> rule;

    SiteType(String id, String displayName, Predicate<SourceSite> rule) {
        this.id = id;
        this.displayName = displayName;
        this.rule = rule;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * @return the generator that built the site in {@code dir}, if recognised
     */
    public static Optional<SiteType> detect(Path dir) {
        SourceSite site = new SourceSite(dir.toAbsolutePath().normalize());
        return Arrays.stream(values()).filter(t -> t.rule.test(site)).findFirst();
    }

    public static Optional<SiteType> fromId(String id) {
        return Arrays.stream(values())
                .filter(t -> t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id))
                .findFirst();
    }

    @Override
    public String toString() {
        return id;
    }
}
