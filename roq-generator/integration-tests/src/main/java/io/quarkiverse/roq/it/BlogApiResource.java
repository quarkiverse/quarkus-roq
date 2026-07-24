package io.quarkiverse.roq.it;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/blog")
@ApplicationScoped
public class BlogApiResource {

    @GET
    @Path("/posts")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BlogPost> getAllPosts() {
        return List.of(
                new BlogPost("hello-world", "Hello World", "First post"),
                new BlogPost("roq-intro", "Introducing Roq", "Static site generator"));
    }

    @GET
    @Path("/posts/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public String getPost(@PathParam("slug") String slug) {
        return "<html><body><h1>" + slug + "</h1></body></html>";
    }
}
