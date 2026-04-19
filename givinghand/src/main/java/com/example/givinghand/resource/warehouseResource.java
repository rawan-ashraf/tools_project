package com.example.givinghand;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;

@Stateless
@Path("/warehouse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WarehouseResource {

    @PersistenceContext(unitName = "givingHandPU")
    private EntityManager em;

    // el awl create el warehouse
    @POST
    @Path("/create")
    @RolesAllowed("organization")
    public Response createWarehouse(Map<String, String> body,
                                    @Context SecurityContext sc) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Response.status(400)
                    .entity(Json.createObjectBuilder()
                            .add("message", "Warehouse name is required.")
                            .build())
                    .build();
        }

        // da by resolve calling el organization
        User org = findUserByEmail(sc.getUserPrincipal().getName());
        if (org == null) {
            return Response.status(401)
                    .entity(Json.createObjectBuilder().add("message", "Organization not found.").build())
                    .build();
        }

        Warehouse warehouse = new Warehouse(name, org);
        em.persist(warehouse);

        return Response.status(201)
                .entity(Json.createObjectBuilder()
                        .add("message", "Warehouse created successfully.")
                        .add("warehouse_id", warehouse.getId())
                        .build())
                .build();
    }

    // Add aw Update Inventory Item
    @POST
    @Path("/{warehouse_id}/add")
    @RolesAllowed("organization")
    public Response addInventory(@PathParam("warehouse_id") long warehouseId,
                                 Map<String, Object> body,
                                 @Context SecurityContext sc) {

        String itemName = (String) body.get("item_name");
        String category = (String) body.get("category");
        Object qtyObj   = body.get("quantity");

        // validate el input
        if (itemName == null || itemName.isBlank()) {
            return badRequest("Field 'item_name' is required.");
        }
        if (category == null || category.isBlank()) {
            return badRequest("Field 'category' is required.");
        }
        if (qtyObj == null) {
            return badRequest("Field 'quantity' is required.");
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyObj.toString());
            if (quantity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            return badRequest("Field 'quantity' must be a positive integer.");
        }

        // verify lw el warehouse exists w belongs to el calling org
        Warehouse warehouse = em.find(Warehouse.class, warehouseId);
        if (warehouse == null) {
            return notFound("Warehouse " + warehouseId + " not found.");
        }

        User org = findUserByEmail(sc.getUserPrincipal().getName());
        if (!warehouse.getOrganization().getId().equals(org.getId())) {
            return Response.status(403)
                    .entity(jsonMsg("You do not own this warehouse."))
                    .build();
        }

        // lw el item already exists, defha le its quantity
        WarehouseItem existing = warehouse.findItem(itemName);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            WarehouseItem newItem = new WarehouseItem(itemName, quantity, category, warehouse);
            em.persist(newItem);
            warehouse.getItems().add(newItem);
        }

        return Response.ok(jsonMsg("Inventory updated successfully.")).build();
    }

    // view el warehouse stock dashboard
    @GET
    @Path("/{warehouse_id}")
    @RolesAllowed("organization")
    public Response viewWarehouse(@PathParam("warehouse_id") long warehouseId,
                                  @Context SecurityContext sc) {

        Warehouse warehouse = em.find(Warehouse.class, warehouseId);
        if (warehouse == null) {
            return notFound("Warehouse " + warehouseId + " not found.");
        }

        User org = findUserByEmail(sc.getUserPrincipal().getName());
        if (!warehouse.getOrganization().getId().equals(org.getId())) {
            return Response.status(403)
                    .entity(jsonMsg("You do not own this warehouse."))
                    .build();
        }

        JsonArrayBuilder itemsArray = Json.createArrayBuilder();
        for (WarehouseItem item : warehouse.getItems()) {
            itemsArray.add(Json.createObjectBuilder()
                    .add("item_name", item.getItemName())
                    .add("category",  item.getCategory())
                    .add("quantity",  item.getQuantity()));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("warehouse_id",   warehouse.getId())
                .add("warehouse_name", warehouse.getName())
                .add("inventory",      itemsArray);

        return Response.ok(response.build()).build();
    }

    // e3ml list le all warehouses for organization
    @GET
    @RolesAllowed("organization")
    public Response listWarehouses(@Context SecurityContext sc) {
        User org = findUserByEmail(sc.getUserPrincipal().getName());
        if (org == null) {
            return Response.status(401).entity(jsonMsg("Organization not found.")).build();
        }

        List<Warehouse> warehouses = em.createQuery(
                        "SELECT w FROM Warehouse w WHERE w.organization.id = :orgId", Warehouse.class)
                .setParameter("orgId", org.getId())
                .getResultList();

        JsonArrayBuilder warehouseArray = Json.createArrayBuilder();
        for (Warehouse w : warehouses) {
            int totalItems = w.getItems().stream().mapToInt(WarehouseItem::getQuantity).sum();
            warehouseArray.add(Json.createObjectBuilder()
                    .add("warehouse_id",   w.getId())
                    .add("warehouse_name", w.getName())
                    .add("total_items",    totalItems)
                    .add("distinct_types", w.getItems().size()));
        }

        return Response.ok(warehouseArray.build()).build();
    }

    // el helpers

    private User findUserByEmail(String email) {
        return em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .getResultStream().findFirst().orElse(null);
    }

    private Response badRequest(String msg) {
        return Response.status(400).entity(jsonMsg(msg)).build();
    }

    private Response notFound(String msg) {
        return Response.status(404).entity(jsonMsg(msg)).build();
    }

    private jakarta.json.JsonObject jsonMsg(String msg) {
        return Json.createObjectBuilder().add("message", msg).build();
    }
}