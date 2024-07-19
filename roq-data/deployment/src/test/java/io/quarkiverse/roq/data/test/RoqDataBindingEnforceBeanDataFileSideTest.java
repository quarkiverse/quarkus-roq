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
                    .add(new StringAsset("quarkus.roq.site-dir=src/test/site\nquarkus.roq.data.enforce-bean=true"),
                            "application.properties"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(
                                "The Roq data configuration is not valid. The data mapping and data files are not matching:")
                        .hasMessageContaining("The data file 'bar' does not match with any @DataMapping class");
            });

    @Test
    void test() {
        Assertions.assertTrue(false);
    }
}
