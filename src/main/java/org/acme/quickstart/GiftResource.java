package org.acme.quickstart;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/gift")
public class GiftResource {

    @Inject
    SantaClausService santaClausService;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String create(@QueryParam("name") String name) {
        santaClausService.createGift(name);
        return "created " + name;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getAll() {
        return santaClausService.findAll().toString();
    }

}
