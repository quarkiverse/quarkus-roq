package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.test.util.Hero;
import io.quarkiverse.roq.data.test.util.HeroList;
import io.quarkus.test.QuarkusUnitTest;

public class RoqDataArrayDirTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class, HeroList.class)
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq"),
                            "application.properties"));

    @Inject
    HeroList heroes;

    @Test
    public void testArrayDir() {
        Assertions.assertNotNull(heroes);
        Assertions.assertEquals(2, heroes.list().size());
        Assertions.assertTrue(heroes.list().stream().anyMatch(h -> "Batman".equals(h.name())));
        Assertions.assertTrue(heroes.list().stream().anyMatch(h -> "Superman".equals(h.name())));
    }
}
