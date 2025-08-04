import java.util.Map;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class SocialBrands {
    public static final Map<String, String> iconMap = Map.ofEntries(
            Map.entry("social-twitter", "fa-brands fa-twitter"),
            Map.entry("social-github", "fa-brands fa-github"),
            Map.entry("social-linkedin", "fa-brands fa-linkedin"),
            Map.entry("social-facebook", "fa-brands fa-facebook"),
            Map.entry("social-youtube", "fa-brands fa-youtube"),
            Map.entry("social-discord", "fa-brands fa-discord"),
            Map.entry("social-email", "fa fa-envelope"),
            Map.entry("social-bluesky", "fa-brands fa-bluesky"),
            Map.entry("social-mastodon", "fa-brands fa-mastodon"),
            Map.entry("social-slack", "fa-brands fa-slack"));

    public static final Map<String, String> urlPrefixMap = Map.ofEntries(
            Map.entry("social-twitter", "https://twitter.com/"),
            Map.entry("social-github", "https://github.com/"),
            Map.entry("social-linkedin", "https://in.linkedin.com/in/"),
            Map.entry("social-facebook", "https://facebook.com/"),
            Map.entry("social-youtube", "https://youtube.com/"),
            Map.entry("social-discord", ""),
            Map.entry("social-email", "mailto:"),
            Map.entry("social-bluesky", "https://bsky.app/profile/"),
            Map.entry("social-mastodon", ""),
            Map.entry("social-slack", ""));
}
