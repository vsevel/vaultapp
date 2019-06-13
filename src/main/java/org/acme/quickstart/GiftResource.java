package org.acme.quickstart;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/gift")
public class GiftResource {

    @Inject
    SantaClausService2 santaClausService;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String create(@QueryParam("name") String name, @QueryParam("ds") String ds) {
        santaClausService.createGift(name, ds);
        return "created " + name;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getAll(@QueryParam("ds") String ds) {
        return santaClausService.findAll(ds).toString();
    }

}
