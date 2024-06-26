package io.quarkiverse.roq.frontmatter.runtime;

import java.util.HashMap;
import java.util.Map;

public class RoqCollections extends HashMap<String, RoqCollection> {

    public RoqCollections(Map<String, RoqCollection> map) {
        super(map);
    }

}
