package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class RoqDataInjectableBeanLookingCustomLocationTest {

    @RegisterExtension
    final static QuarkusUnitTest devMode = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .add(new StringAsset("quarkus.roq.site-dir=test-dir\nquarkus.roq.data.dir=test-data\n"),
                            "application.properties")
                    .addAsResource("test-dir/"));

    @Inject
    @Named("foo")
    JsonObject foo;

    @Inject
    @Named("bar")
    JsonObject bar;

    @Inject
    @Named("baz")
    JsonObject baz;

    @Test
    public void foo() {
        String fromYaml = foo.getString("name");

        Assertions.assertEquals("Super Heroes from Json custom", fromYaml);
    }

    @Test
    public void bar() {
        String fromYml = bar.getString("name");

        Assertions.assertEquals("Super Heroes from Yaml custom", fromYml);
    }

    @Test
    public void baz() {
        String fromYml = baz.getString("name");
        Assertions.assertEquals("Super Heroes from Yml custom", fromYml);
    }

}
