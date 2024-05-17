package io.quarkiverse.statiq.runtime;

import java.util.List;
import java.util.Objects;

public class StatiqGeneratorConfig {

    public List<String> fixedPaths;
    public String outputDir;

    public StatiqGeneratorConfig(List<String> fixedPaths, String outputDir) {
        this.fixedPaths = fixedPaths;
        this.outputDir = outputDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StatiqGeneratorConfig that = (StatiqGeneratorConfig) o;
        return Objects.equals(fixedPaths, that.fixedPaths) && Objects.equals(outputDir, that.outputDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fixedPaths, outputDir);
    }
}
