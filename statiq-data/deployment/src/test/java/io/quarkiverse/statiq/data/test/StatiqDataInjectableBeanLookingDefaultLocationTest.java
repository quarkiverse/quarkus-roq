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
                    .addAsResource("application.json", "META-INF/resources/data/application.json")
                    .addAsResource("application.yaml", "META-INF/resources/data/application.yaml")
                    .addAsResource("application.yml", "META-INF/resources/data/application.yml"));

    @Inject
    Instance<SimpleBean> simpleBean;

    @Test
    public void whenUseApplicationJsonShouldGetSuperHeroesJson() {
        String fromJson = simpleBean.get().getNameFromApplicationJson();

        Assertions.assertEquals("Super Heroes from Json", fromJson);
    }

    @Test
    public void whenUseApplicationYamlShouldGetSuperHeroesYaml() {
        String fromYaml = simpleBean.get().getNameFromApplicationYaml();

        Assertions.assertEquals("Super Heroes from Yaml", fromYaml);
    }

    @Test
    public void whenUseApplicationYmlShouldGetSuperHeroesYml() {
        String fromYml = simpleBean.get().getNameFromApplicationYml();

        Assertions.assertEquals("Super Heroes from Yml", fromYml);
    }

    @Singleton
    static class SimpleBean {

        @Inject
        @Named("application.json")
        JsonObject applicationJson;

        @Inject
        @Named("application.yaml")
        JsonObject applicationYaml;

        @Inject
        @Named("application.yml")
        JsonObject applicationYml;

        public String getNameFromApplicationJson() {
            return applicationJson.getString("name");
        }

        public String getNameFromApplicationYaml() {
            return applicationYaml.getString("name");
        }

        public String getNameFromApplicationYml() {
            return applicationYml.getString("name");
        }

    }
}
