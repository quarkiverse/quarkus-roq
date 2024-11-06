package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class RoqDataBindingEnforceBeanDataFileSideTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusUnitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq\nquarkus.roq.data.enforce-bean=true"),
                            "application.properties"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(
                                "Roq data is configured to enforce beans for data. Some data mapping and data files are not matching:")
                        .hasMessageContaining("The data file 'bar' does not match with any @DataMapping class");
            });

    @Test
    void test() {
        Assertions.assertTrue(false);
    }
}
