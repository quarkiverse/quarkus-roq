package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RoqCollection extends ArrayList<Page> {

    public RoqCollection(List<Page> pages) {
        super(pages.stream().sorted(Comparator.comparing(Page::date).reversed()).toList());
    }

}
