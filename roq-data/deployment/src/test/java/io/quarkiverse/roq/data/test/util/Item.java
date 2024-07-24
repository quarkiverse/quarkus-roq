package io.quarkiverse.roq.data.test.util;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping("list")
public class Item {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
