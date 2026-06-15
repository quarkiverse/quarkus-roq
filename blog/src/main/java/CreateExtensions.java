import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@TemplateExtension
public class CreateExtensions {

    private static final String PREFIX = "io.quarkiverse.roq:quarkus-roq-";

    public static RawString toCreateJson(RoqCollection collection) {
        JsonArray result = new JsonArray();
        for (DocumentPage item : collection) {
            String kind = item.data().getString("kind");
            if ("plugin".equals(kind) || "theme".equals(kind)) {
                String installName = item.data().getString("install-name");
                String extensionId = "base".equals(installName) && "theme".equals(kind)
                        ? null
                        : PREFIX + kind + "-" + installName;
                result.add(new JsonObject()
                        .put("kind", kind)
                        .put("name", item.title())
                        .put("installName", installName)
                        .put("extensionId", extensionId)
                        .put("description", item.description())
                        .put("icon", item.data().getString("icon", ""))
                        .put("tags", item.data().getJsonArray("tags", new JsonArray())));
            }
        }
        return new RawString(result.encodePrettily());
    }
}
