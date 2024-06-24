package io.quarkiverse.statiq.data.test;

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

public class StatiqDataInjectableBeanLookingDefaultLocationTest {

    @RegisterExtension
    final static QuarkusUnitTest devMode = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(SimpleBean.class)
                    .add(new StringAsset(""),
                            "application.properties")
                    .addAsResource("foo.json", "META-INF/resources/data/foo.json")
                    .addAsResource("foo.yaml", "META-INF/resources/data/foo.yaml")
                    .addAsResource("foo.yml", "META-INF/resources/data/foo.yml"));

    @Inject
    Instance<SimpleBean> simpleBean;

    @Test
    public void whenUseFooJsonShouldGetSuperHeroesJson() {
        String fromJson = simpleBean.get().getNameFromFooJson();

        Assertions.assertEquals("Super Heroes from Json", fromJson);
    }

    @Test
    public void whenUseFooYamlShouldGetSuperHeroesYaml() {
        String fromYaml = simpleBean.get().getNameFromFooYaml();

        Assertions.assertEquals("Super Heroes from Yaml", fromYaml);
    }

    @Test
    public void whenUseFooYmlShouldGetSuperHeroesYml() {
        String fromYml = simpleBean.get().getNameFromFooYml();

        Assertions.assertEquals("Super Heroes from Yml", fromYml);
    }

    @Singleton
    static class SimpleBean {

        @Inject
        @Named("foo.json")
        JsonObject fooJson;

        @Inject
        @Named("foo.yaml")
        JsonObject fooYaml;

        @Inject
        @Named("foo.yml")
        JsonObject fooYml;

        public String getNameFromFooJson() {
            return fooJson.getString("name");
        }

        public String getNameFromFooYaml() {
            return fooYaml.getString("name");
        }

        public String getNameFromFooYml() {
            return fooYml.getString("name");
        }

    }
}
