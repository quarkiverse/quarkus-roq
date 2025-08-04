import io.quarkus.qute.TemplateGlobal;
import java.util.List;
import java.util.Map;

@TemplateGlobal
public class SocialBrands {

    public static List<Map<String, String>> brands = List.of(
            Map.of(
                    "id", "twitter",
                    "icon", "fa-brands fa-twitter",
                    "url", "https://twitter.com/quarkusio"
            ),
            Map.of(
                    "id", "github",
                    "icon", "fa-brands fa-github",
                    "url", "https://github.com/quarkiverse/quarkus-roq"
            ),
            Map.of(
                    "id", "linkedin",
                    "icon", "fa-brands fa-linkedin",
                    "url", "https://linkedin.com/in/quarkusio"
            )
    );
}
