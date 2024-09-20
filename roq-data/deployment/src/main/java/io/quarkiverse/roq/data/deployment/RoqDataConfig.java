package io.quarkiverse.roq.data.deployment;

import java.util.Objects;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.data")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqDataConfig {

    String DEFAULT_DIR = "_data";

    /**
     * The location of the Roq data files relative to the quarkus.roq.dir.
     */
    @WithDefault(DEFAULT_DIR)
    String dir();

    /**
     * Whether to enforce the use of a bean for each data file.
     * <br>
     * With this option enabled, when a record is annotated with {@link DataMapping}, a bean will be created and populated
     * with the data from the file.
     */
    @WithDefault("false")
    boolean enforceBean();

    static boolean isEqual(RoqDataConfig q1, RoqDataConfig q2) {
        return Objects.equals(q1.dir(), q2.dir()) && Objects.equals(q1.enforceBean(), q2.enforceBean());
    }

}
