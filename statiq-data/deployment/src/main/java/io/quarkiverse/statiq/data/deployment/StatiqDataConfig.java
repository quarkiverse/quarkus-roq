package io.quarkiverse.statiq.data.deployment;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "statiq.data")
public class StatiqDataConfig {

    private final static String DEFAULT_LOCATION = "data";

    /**
     * The location of the Statiq data files.
     */
    @ConfigItem(defaultValue = DEFAULT_LOCATION)
    public String location;
}
