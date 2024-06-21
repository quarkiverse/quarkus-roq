package io.quarkiverse.statiq.data.test;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
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
                    .addAsResource("application.json", "META-INF/resources/data/application.json"));

    @Inject
    Instance<SimpleBean> simpleBean;

    @Test
    public void writeYourOwnProdModeTest() {

        String name = simpleBean.get().getName();
        assert name.equals("Super Heroes");
    }

    @Singleton
    static class SimpleBean {

        @Inject
        @Named("application.json")
        JsonObject jsonObject;

        public String getName() {
            return jsonObject.getString("name");
        }

    }
}
