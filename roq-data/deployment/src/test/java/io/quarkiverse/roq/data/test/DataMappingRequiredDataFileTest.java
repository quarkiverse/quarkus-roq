package io.quarkiverse.roq.data.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkus.test.QuarkusUnitTest;

public class DataMappingRequiredDataFileTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(DataMappingRequiredDataFileTest.Person.class))
            .assertException(throwable -> {
                Assertions.assertThat(throwable)
                        .isInstanceOf(io.quarkiverse.roq.data.deployment.exception.DataMappingRequiredFileException.class)
                        .hasMessageContaining("Required data file not found")
                        .hasMessageContaining("@DataMapping(\"foo\") is marked as required");
            });

    @DataMapping(value = "foo", required = true)
    public record Person(String name) {
    }

    @Test
    public void assertFail() {

    }
}
