package com.iamroq;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.narayana.jta.QuarkusTransaction;

@Path("/contributors")
public class ContributorsResource {

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public void addContributor(String contributor) {
        QuarkusTransaction.requiringNew()
                .run(() -> {
                    Contributors contributors = new Contributors(contributor);
                    Contributors.persist(contributors);
                });
    }
}
