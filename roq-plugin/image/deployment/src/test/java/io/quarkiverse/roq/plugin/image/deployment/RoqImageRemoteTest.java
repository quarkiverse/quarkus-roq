package io.quarkiverse.roq.plugin.image.deployment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.net.InetSocketAddress;
import java.nio.file.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * A page image that is a remote url is downloaded and converted at build time: the tag and {@code img:url} render
 * generated variants.
 */
public class RoqImageRemoteTest {

    // the test class is loaded twice (the test and the application classloaders): one server, shared by property
    static final String BASE_PROPERTY = RoqImageRemoteTest.class.getName() + ".base";
    static final String BASE = System.getProperty(BASE_PROPERTY) == null ? serveTestClasses()
            : System.getProperty(BASE_PROPERTY);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "remote-site")
            // the fixture server is plain http
            .overrideConfigKey("quarkus.qute.image.remote.allow-http", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("---\ntitle: Remote\nimage: " + BASE
                            + "/unknown-preset-site/public/images/hero.jpg\n---\n"
                            + "<section class=\"remote\">{#img src=page.image alt='Remote'/}</section>\n"
                            + "<p id=\"url\">{img:url(page.image)}</p>"), "remote-site/content/index.html"));

    @Test
    public void testRemotePageImageIsConverted() {
        String body = RestAssured.get("/").then().statusCode(200).extract().asString();
        assertThat(body, containsString("<img src=\"/static/images/generated/"));
        assertThat(body, containsString("<p id=\"url\">/static/images/generated/"));
    }

    private static String serveTestClasses() {
        HttpServer server = SimpleFileServer.createFileServer(new InetSocketAddress("localhost", 0),
                Path.of("target/test-classes").toAbsolutePath(), SimpleFileServer.OutputLevel.NONE);
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        System.setProperty(BASE_PROPERTY, base);
        return base;
    }
}
