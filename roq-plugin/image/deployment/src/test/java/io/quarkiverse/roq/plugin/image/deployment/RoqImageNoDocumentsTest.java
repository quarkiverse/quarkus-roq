package io.quarkiverse.roq.plugin.image.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A site without documents (a fresh blog, or one whose posts are all drafts) still builds when a listing uses
 * {@code post.image}: a document root with nothing to bind is zero contexts, not a failure.
 */
public class RoqImageNoDocumentsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "no-documents-site")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("no-documents-site"));

    @Test
    public void testBuildPassesAndIndexRenders() {
        RestAssured.when().get("/").then().statusCode(200);
    }
}
