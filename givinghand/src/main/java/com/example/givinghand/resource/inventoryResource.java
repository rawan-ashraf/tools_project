package com.example.givinghand;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

@Stateless
@Path("/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    // Low-stock absolute threshold
    private static final int LOW_STOCK_THRESHOLD = 10;

    @PersistenceContext(unitName = "givingHandPU")
    private EntityManager em;

    @Inject
    private NotificationSender notificationSender;

    // Allocating el resources
    @POST
    @Path("/allocate")
    @RolesAllowed("organization")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Response allocate(Map<String, Object> body,
                             @Context SecurityContext sc) {

        // parse w validate el input
        Object whObj   = body.get("warehouse_id");
        Object campObj = body.get("campaign_id");
        String itemName = (String) body.get("item_name");
        Object qtyObj  = body.get("quantity");

        if (whObj == null || campObj == null || itemName == null || qtyObj == null) {
            return badRequest("Fields warehouse_id, campaign_id, item_name and quantity are all required.");
        }

        long warehouseId, campaignId;
        int  quantity;
        try {
            warehouseId = Long.parseLong(whObj.toString());
            campaignId  = Long.parseLong(campObj.toString());
            quantity    = Integer.parseInt(qtyObj.toString());
            if (quantity <= 0) throw new NumberFormatException("quantity must be > 0");
        } catch (NumberFormatException e) {
            return badRequest("warehouse_id, campaign_id must be valid IDs and quantity must be a positive integer.");
        }

        // Authorisation, el caller lazem y-own the warehouse
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

        // ── Step 1, Decrease el warehouse stock
        WarehouseItem warehouseItem = warehouse.findItem(itemName);
        if (warehouseItem == null) {
            return badRequest("Item '" + itemName + "' does not exist in warehouse " + warehouseId + ".");
        }
        if (warehouseItem.getQuantity() < quantity) {
            return badRequest("Insufficient stock. Available: "
                    + warehouseItem.getQuantity() + ", Requested: " + quantity + ".");
        }

        warehouseItem.setQuantity(warehouseItem.getQuantity() - quantity);

        // Step 2, Increase el campaign "Received" count
        CampaignNeedItem needItem = em.createQuery(
                        "SELECT n FROM CampaignNeedItem n " +
                                "WHERE n.campaign.id = :cid AND LOWER(n.itemName) = LOWER(:name)",
                        CampaignNeedItem.class)
                .setParameter("cid", campaignId)
                .setParameter("name", itemName)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (needItem == null) {
            throw new IllegalArgumentException(
                    "Item '" + itemName + "' is not in the need list of campaign " + campaignId + ".");
        }

        needItem.setReceivedQuantity(needItem.getReceivedQuantity() + quantity);


        System.out.println("[InventoryResource] Allocated " + quantity + "x " + itemName
                + " from warehouse " + warehouseId + " to campaign " + campaignId);

        int remainingAfter = warehouseItem.getQuantity();
        if (remainingAfter <= LOW_STOCK_THRESHOLD) {
            System.out.println("[InventoryResource] Low stock detected for '" + itemName
                    + "' in warehouse " + warehouseId + ". Sending JMS alert.");
            notificationSender.sendLowStockAlert(warehouseId, itemName, remainingAfter);
        }

        return Response.ok(
                        jsonMsg("Resources allocated from warehouse to campaign successfully."))
                .build();
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