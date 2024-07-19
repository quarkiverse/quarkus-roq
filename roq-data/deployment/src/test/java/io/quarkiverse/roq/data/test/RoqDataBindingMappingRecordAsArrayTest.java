package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.test.util.ItemRecord;
import io.quarkus.test.QuarkusUnitTest;

public class RoqDataBindingMappingRecordAsArrayTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusUnitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(ItemRecord.class)
                    .add(new StringAsset("quarkus.roq.site-dir=src/test/site"),
                            "application.properties"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(
                                "You are trying to map the file list(.json|.yaml|.yml) to the class io.quarkiverse.roq.data.test.util.ItemRecord");
            });

    @Test
    void test() {
        Assertions.assertTrue(false);
    }
}
