package io.quarkiverse.roq.theme;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

@TemplateExtension(namespace = "social")
public class SocialBrands {

    public record ShareLink(String icon, String label, String urlTemplate) {

        public String url(String title, String absoluteUrl) {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String encodedUrl = URLEncoder.encode(absoluteUrl, StandardCharsets.UTF_8);
            return urlTemplate.replace("{title}", encodedTitle).replace("{url}", encodedUrl);
        }
    }

    public record ContactLink(String icon, String url) {
    }

    private record ContactBrand(String icon, String prefix) {
    }

    private static final Map<String, ShareLink> SHARES;
    static {
        Map<String, ShareLink> map = new LinkedHashMap<>();
        map.put("share-x", new ShareLink("fa-brands fa-x-twitter", "X",
                "https://x.com/intent/post?text={title}&url={url}"));
        map.put("share-facebook", new ShareLink("fa-brands fa-facebook", "Facebook",
                "https://www.facebook.com/sharer/sharer.php?u={url}"));
        map.put("share-bluesky", new ShareLink("fa-brands fa-bluesky", "Bluesky",
                "https://bsky.app/intent/compose?text={title}%0A{url}"));
        map.put("share-linkedin", new ShareLink("fa-brands fa-linkedin", "LinkedIn",
                "https://www.linkedin.com/sharing/share-offsite/?url={url}"));
        SHARES = Collections.unmodifiableMap(map);
    }

    private static final Map<String, ContactBrand> CONTACTS = Map.ofEntries(
            Map.entry("social-x", new ContactBrand("fa-brands fa-x-twitter", "https://x.com/")),
            Map.entry("social-twitter", new ContactBrand("fa-brands fa-x-twitter", "https://x.com/")),
            Map.entry("social-github", new ContactBrand("fa-brands fa-github", "https://github.com/")),
            Map.entry("social-linkedin", new ContactBrand("fa-brands fa-linkedin", "https://www.linkedin.com/in/")),
            Map.entry("social-linkedin-company",
                    new ContactBrand("fa-brands fa-linkedin", "https://www.linkedin.com/company/")),
            Map.entry("social-facebook", new ContactBrand("fa-brands fa-facebook", "https://facebook.com/")),
            Map.entry("social-youtube", new ContactBrand("fa-brands fa-youtube", "https://youtube.com/")),
            Map.entry("social-discord", new ContactBrand("fa-brands fa-discord", "https://discord.gg/")),
            Map.entry("social-email", new ContactBrand("fa fa-envelope", "mailto:")),
            Map.entry("social-bluesky", new ContactBrand("fa-brands fa-bluesky", "https://bsky.app/profile/")),
            Map.entry("social-mastodon", new ContactBrand("fa-brands fa-mastodon", "")),
            Map.entry("social-slack", new ContactBrand("fa-brands fa-slack", "")),
            Map.entry("social-whatsapp", new ContactBrand("fa-brands fa-whatsapp", "https://wa.me/")),
            Map.entry("social-instagram", new ContactBrand("fa-brands fa-instagram", "https://instagram.com/")),
            Map.entry("social-telegram", new ContactBrand("fa-brands fa-telegram", "https://t.me/")));

    static List<ShareLink> share(JsonObject data) {
        List<ShareLink> result = new ArrayList<>();
        for (var entry : SHARES.entrySet()) {
            Object val = data.getValue(entry.getKey());
            if (Boolean.FALSE.equals(val) || "false".equals(val)) {
                continue;
            }
            result.add(entry.getValue());
        }
        for (String key : data.fieldNames()) {
            if (key.startsWith("share-") && !SHARES.containsKey(key)) {
                Object val = data.getValue(key);
                if (val instanceof JsonObject obj) {
                    result.add(new ShareLink(
                            obj.getString("icon", "fa-solid fa-share-nodes"),
                            obj.getString("label", key.substring(6)),
                            obj.getString("url", "")));
                }
            }
        }
        return result;
    }

    static List<ContactLink> contact(JsonObject data) {
        List<ContactLink> result = new ArrayList<>();
        for (var entry : CONTACTS.entrySet()) {
            Object val = data.getValue(entry.getKey());
            if (val != null && !val.toString().isEmpty()) {
                result.add(new ContactLink(entry.getValue().icon(), entry.getValue().prefix() + val));
            }
        }
        return result;
    }
}
