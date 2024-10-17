package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqDataInjectableBeanLookingDefaultLocationTest {

    @RegisterExtension
    final static QuarkusUnitTest devMode = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("fromResource.json", "data/fromResource.json")
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq"),
                            "application.properties"));

    @Inject
    @Named("foo")
    JsonObject foo;

    @Inject
    @Named("bar")
    JsonObject bar;

    @Inject
    @Named("baz")
    JsonObject baz;

    @Inject
    @Named("list")
    JsonArray list;

    @Inject
    @Named("fromResource")
    JsonObject fromResource;

    @Test
    public void foo() {
        String fromJson = foo.getString("name");
        Assertions.assertEquals("Super Heroes from Json", fromJson);
    }

    @Test
    public void bar() {
        String fromYaml = bar.getString("name");
        Assertions.assertEquals("Super Heroes from Yaml", fromYaml);
    }

    @Test
    public void baz() {
        String fromYml = baz.getString("name");
        Assertions.assertEquals("Super Heroes from Yml", fromYml);
    }

    @Test
    public void list() {
        Assertions.assertEquals(3, list.size());
    }

    @Test
    public void fromResource() {
        Assertions.assertEquals("Hello from resource", fromResource.getString("name"));
    }

}
