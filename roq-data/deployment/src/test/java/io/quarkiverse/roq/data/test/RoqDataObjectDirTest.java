package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.test.util.Hero;
import io.quarkiverse.roq.data.test.util.HeroMap;
import io.quarkus.test.QuarkusUnitTest;

public class RoqDataObjectDirTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class, HeroMap.class)
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq"),
                            "application.properties"));

    @Inject
    HeroMap heroes;

    @Test
    public void testObjectDir() {
        Assertions.assertNotNull(heroes);
        Assertions.assertEquals(2, heroes.map().size());
        Assertions.assertEquals("Batman", heroes.map().get("batman").name());
        Assertions.assertEquals("Gotham", heroes.map().get("batman").city());
        Assertions.assertEquals("Superman", heroes.map().get("superman").name());
        Assertions.assertEquals("Metropolis", heroes.map().get("superman").city());
    }
}
