import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class ReleasesExtensions {

    private static final List<String> RELEASE_TYPES = List.of("major", "minor", "patch");

    static List<DocumentPage> releasePosts(RoqCollection collection) {
        return collection.stream()
                .filter(ReleasesExtensions::isRelease)
                .toList();
    }

    static boolean isRelease(Page page) {
        return hasTag(page, "release");
    }

    static boolean isBreaking(Page page) {
        return hasTag(page, "breaking");
    }

    static String releaseType(Page page) {
        List<String> tags = slugifiedTags(page);
        return RELEASE_TYPES.stream()
                .filter(tags::contains)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasTag(Page page, String tag) {
        return slugifiedTags(page).contains(tag);
    }

    private static List<String> slugifiedTags(Page page) {
        Object tags = page.data().getValue("tags");
        if (tags == null) {
            return List.of();
        }
        return RoqTemplateExtension.asStrings(tags).stream()
                .map(RoqTemplateExtension::slugify)
                .toList();
    }
}
