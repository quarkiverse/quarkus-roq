package io.quarkiverse.roq;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@RoqAndRoll
@TestProfile(RoqAndRollTest.RoqAndRollProfile.class)
public class RoqAndRollTest extends AbstractRoqTest {

    public static class RoqAndRollProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "roq-and-roll";
        }
    }
}
