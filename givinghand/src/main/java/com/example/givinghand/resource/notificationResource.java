package com.example.givinghand;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;

@Stateless
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @PersistenceContext(unitName = "givingHandPU")
    private EntityManager em;

    @GET
    @RolesAllowed({"organization", "donor"})
    public Response getNotifications(@Context SecurityContext sc) {
        String email = sc.getUserPrincipal().getName();

        User user = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .getResultStream().findFirst().orElse(null);

        if (user == null) {
            return Response.status(401)
                    .entity(Json.createObjectBuilder().add("message", "User not found.").build())
                    .build();
        }

        List<Notification> notifications = em.createQuery(
                        "SELECT n FROM Notification n WHERE n.recipient.id = :uid ORDER BY n.timestamp DESC",
                        Notification.class)
                .setParameter("uid", user.getId())
                .getResultList();

        JsonArrayBuilder array = Json.createArrayBuilder();
        for (Notification n : notifications) {
            array.add(Json.createObjectBuilder()
                    .add("event_type", n.getEventType())
                    .add("message",    n.getMessage())
                    .add("timestamp",  n.getTimestamp().toString()));
        }

        return Response.ok(array.build()).build();
    }
}