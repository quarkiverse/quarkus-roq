package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.test.util.Bar;
import io.quarkus.test.QuarkusUnitTest;

public class RoqDataBindingEnforceBeanDataMappingSideTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusUnitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(Bar.class)
                    .add(new StringAsset("quarkus.roq.dir=src/test/site\nquarkus.roq.data.enforce-bean=true"),
                            "application.properties"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(
                                "Roq data is configured to enforce beans for data. Some data mapping and data files are not matching:")
                        .hasMessageContaining("The @DataMapping#value('why') does not match with any data file");
            });

    @Test
    void test() {
        Assertions.assertTrue(false);
    }
}
