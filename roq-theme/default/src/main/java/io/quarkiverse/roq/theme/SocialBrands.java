package io.quarkiverse.roq.theme;

import java.util.Map;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class SocialBrands {
    private record SocialBrand(String icon, String prefix) {
    }

    public static final Map<String, SocialBrand> brand = Map.ofEntries(
            Map.entry("social-twitter", new SocialBrand("fa-brands fa-twitter", "https://twitter.com/")),
            Map.entry("social-github", new SocialBrand("fa-brands fa-github", "https://github.com/")),
            Map.entry("social-linkedin", new SocialBrand("fa-brands fa-linkedin", "https://in.linkedin.com/in/")),
            Map.entry("social-linkedin-company", new SocialBrand("fa-brands fa-linkedin", "https://in.linkedin.com/company/")),
            Map.entry("social-facebook", new SocialBrand("fa-brands fa-facebook", "https://facebook.com/")),
            Map.entry("social-youtube", new SocialBrand("fa-brands fa-youtube", "https://youtube.com/")),
            Map.entry("social-discord", new SocialBrand("fa-brands fa-discord", "https://discord.gg/")),
            Map.entry("social-email", new SocialBrand("fa fa-envelope", "mailto:")),
            Map.entry("social-bluesky", new SocialBrand("fa-brands fa-bluesky", "https://bsky.app/profile/")),
            Map.entry("social-mastodon", new SocialBrand("fa-brands fa-mastodon", "")),
            Map.entry("social-slack", new SocialBrand("fa-brands fa-slack", "")));
}