package io.quarkiverse.roq.theme.thecode;

import java.util.Map;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class SocialBrands {
    public record SocialBrand(String icon, String prefix) {
    }

    public static final Map<String, SocialBrand> brand = Map.ofEntries(
            Map.entry("social-twitter", new SocialBrand("icon-twitter", "https://twitter.com/")),
            Map.entry("social-github", new SocialBrand("icon-github", "https://github.com/")),
            Map.entry("social-linkedin", new SocialBrand("icon-linkedin", "https://in.linkedin.com/in/")),
            Map.entry("social-linkedin-company", new SocialBrand("icon-linkedin", "https://in.linkedin.com/company/")),
            Map.entry("social-facebook", new SocialBrand("icon-facebook", "https://facebook.com/")),
            Map.entry("social-youtube", new SocialBrand("icon-youtube", "https://youtube.com/")),
            Map.entry("social-discord", new SocialBrand("icon-discord", "https://discord.gg/")),
            Map.entry("social-email", new SocialBrand("icon-email", "mailto:")),
            Map.entry("social-bluesky", new SocialBrand("icon-bluesky", "https://bsky.app/profile/")),
            Map.entry("social-mastodon", new SocialBrand("icon-mastodon", "")),
            Map.entry("social-slack", new SocialBrand("icon-slack", "")),
            Map.entry("social-whatsapp", new SocialBrand("icon-whatsapp", "https://wa.me/")),
            Map.entry("social-instagram", new SocialBrand("icon-instagram", "https://instagram.com/")),
            Map.entry("social-telegram", new SocialBrand("icon-telegram", "https://t.me/")));
}
