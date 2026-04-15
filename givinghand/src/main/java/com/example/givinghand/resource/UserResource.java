package com.example.givinghand.resource;
import com.example.givinghand.dto.Request;
import com.example.givinghand.service.UserService;
import com.example.givinghand.dto.login;
import com.example.givinghand.dto.update;
import com.example.givinghand.entity.user;
import com.example.givinghand.util.Validation;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    @EJB
    private UserService userService;
    @PermitAll
    @POST // create data
    @Path("/register")
    public Response register(Request req) { // receives JSON input mapped into request

        String result = userService.register(req); // send data to ejb

        if (result.equals("User registered successfully.")) {
            return Response.status(Response.Status.CREATED)//201
                    .entity("{\"message\":\"" + result + "\"}")
                    .build();
        }

        return Response.status(Response.Status.BAD_REQUEST)//error 400
                .entity("{\"message\":\"" + result + "\"}")
                .build();
    }
    @PermitAll
    @POST
    @Path("/login")
    public Response login(login req) {

        String result = userService.login(req);

        if (result.equals("Login successful")) {
            return Response.ok("{\"message\":\"Login successful\"}").build();
        }

        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"message\":\"" + result + "\"}")
                .build();
    }
    @PUT
    @Path("/profile")
    public Response updateProfile(update req) {

        String result = userService.updateProfile(req);

        if (result.equals("Profile updated successfully")) {
            return Response.ok("{\"message\":\"" + result + "\"}").build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"" + result + "\"}")
                .build();
    }


    @GET
    @Path("/test")
    public String test() {
        return "WORKING";

}
    @RolesAllowed("donor")
    @POST
    @Path("/donations/commit")
    public Response commitDonation() {
        return Response.ok("Donation committed").build();
    }
    @RolesAllowed("organization")
    @POST
    @Path("/campaign/create")
    public Response createCampaign() {
        return Response.ok("Campaign created").build();
    }



}

