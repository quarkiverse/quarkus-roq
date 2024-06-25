package io.quarkiverse.roq.data.deployment;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "roq.data")
public class RoqDataConfig {

    private final static String DEFAULT_LOCATION = "data";

    /**
     * The location of the Roq data files.
     */
    @ConfigItem(defaultValue = DEFAULT_LOCATION)
    public String location;
}
