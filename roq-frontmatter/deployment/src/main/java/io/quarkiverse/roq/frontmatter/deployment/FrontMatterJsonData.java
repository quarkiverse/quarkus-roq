package io.quarkiverse.roq.frontmatter.deployment;

import java.util.Map;
import java.util.Stack;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.vertx.core.json.JsonObject;

public class FrontMatterJsonData {

    public static JsonObject mergeParents(RoqFrontMatterRawTemplateBuildItem item,
            Map<String, RoqFrontMatterRawTemplateBuildItem> byPath) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.data());
        while (parent != null) {
            if (byPath.containsKey(parent)) {
                final RoqFrontMatterRawTemplateBuildItem parentItem = byPath.get(parent);
                parent = parentItem.layout();
                fms.push(parentItem.data());
            } else {
                parent = null;
            }
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }
}
