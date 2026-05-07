package io.quarkiverse.roq.data.test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class RoqDataDirectoryGroupingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .add(new StringAsset("quarkus.roq.dir=src/test/roq"),
                            "application.properties"));

    @Inject
    @Named("heroes")
    JsonObject heroes;

    @Inject
    @Named("heroes/batman")
    JsonObject batman;

    @Test
    public void testGroupedDirectory() {
        Assertions.assertNotNull(heroes);
        Assertions.assertEquals(2, heroes.size());
        Assertions.assertNotNull(heroes.getJsonObject("batman"));
        Assertions.assertEquals("Batman", heroes.getJsonObject("batman").getString("name"));
        Assertions.assertNotNull(heroes.getJsonObject("superman"));
        Assertions.assertEquals("Superman", heroes.getJsonObject("superman").getString("name"));
    }

    @Test
    public void testIndividualFile() {
        Assertions.assertNotNull(batman);
        Assertions.assertEquals("Batman", batman.getString("name"));
        Assertions.assertEquals("Gotham", batman.getString("city"));
    }
}
