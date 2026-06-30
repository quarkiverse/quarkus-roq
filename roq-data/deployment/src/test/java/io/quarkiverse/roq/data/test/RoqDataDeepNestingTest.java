package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

public class RoqDataDeepNestingTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq"),
                            "application.properties"));

    @Inject
    @Named("worlds")
    JsonObject worlds;

    @Test
    public void testDeepNestedGrouping() {
        Assertions.assertNotNull(worlds);
        JsonObject earth = worlds.getJsonObject("earth");
        Assertions.assertNotNull(earth, "Expected 'earth' key in worlds");
        JsonObject earthHeroes = earth.getJsonObject("heroes");
        Assertions.assertNotNull(earthHeroes, "Expected 'heroes' key in earth");
        Assertions.assertEquals("Shadowcat", earthHeroes.getJsonObject("shadowcat").getString("name"));
        Assertions.assertEquals("Storm", earthHeroes.getJsonObject("storm").getString("name"));
    }

    @Test
    public void testDeepNestedMultipleSubdirs() {
        JsonObject krypton = worlds.getJsonObject("krypton");
        Assertions.assertNotNull(krypton, "Expected 'krypton' key in worlds");
        JsonObject kryptonHeroes = krypton.getJsonObject("heroes");
        Assertions.assertNotNull(kryptonHeroes, "Expected 'heroes' key in krypton");
        Assertions.assertEquals("Supergirl", kryptonHeroes.getJsonObject("supergirl").getString("name"));
    }
}
