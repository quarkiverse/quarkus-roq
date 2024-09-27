package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record Paginator(
        String collection,
        int collectionSize,
        int limit,
        int total,
        int currentIndex,
        Integer previousIndex,
        RoqUrl previous,
        Integer nextIndex,
        RoqUrl next) {

    public Integer prevIndex() {
        return previousIndex();
    }

    public RoqUrl prev() {
        return previous();
    }

    public boolean isFirst() {
        return currentIndex == 1;
    }

    public boolean isSecond() {
        return currentIndex == 2;
    }
}
