package io.quarkiverse.roq.data.test;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

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
                    .addClass(SimpleBean.class)
                    .add(new StringAsset("quarkus.roq.site-dir=./\nquarkus.roq.data.dir=test-data\n"),
                            "application.properties"));

    @Inject
    Instance<SimpleBean> simpleBean;

    @Test
    public void whenUseFooYamlShouldGetSuperHeroesYaml() {
        String fromYaml = simpleBean.get().getNameFromBar();

        Assertions.assertEquals("Super Heroes from Yaml custom", fromYaml);
    }

    @Test
    public void whenUseFooYmlShouldGetSuperHeroesYml() {
        String fromYml = simpleBean.get().getNameFromBaz();

        Assertions.assertEquals("Super Heroes from Yml custom", fromYml);
    }

    @Singleton
    static class SimpleBean {

        @Inject
        @Named("foo")
        JsonObject foo;

        @Inject
        @Named("bar")
        JsonObject bar;

        @Inject
        @Named("baz")
        JsonObject baz;

        public String getNameFromFoo() {
            return foo.getString("name");
        }

        public String getNameFromBar() {
            return bar.getString("name");
        }

        public String getNameFromBaz() {
            return baz.getString("name");
        }

    }
}
