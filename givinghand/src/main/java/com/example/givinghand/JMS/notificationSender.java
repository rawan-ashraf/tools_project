package com.example.givinghand;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.jms.*;
import jakarta.json.Json;

@Stateless
public class NotificationSender {

    // injected by el container defined fe standalone.xml / web.xml
    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/queue/GivingHandNotifications")
    private Queue notificationQueue;

    // el low-stock alert
    public void sendLowStockAlert(long warehouseId, String itemName, int remaining) {
        String json = Json.createObjectBuilder()
                .add("event_type", "STOCK_LOW_ALERT")
                .add("warehouse_id", warehouseId)
                .add("item_name", itemName)
                .add("remaining_quantity", remaining)
                .add("message", "Warning: " + itemName + " stock is below 10% in Warehouse " + warehouseId + ".")
                .build()
                .toString();

        sendMessage(json);
    }

    // alert eno we recieved el donation
    public void sendDonationReceived(String donorEmail, int quantity, String itemName) {
        String json = Json.createObjectBuilder()
                .add("event_type", "DONATION_RECEIVED")
                .add("donor_email", donorEmail)
                .add("item_name", itemName)
                .add("quantity", quantity)
                .add("message", "Your " + quantity + " " + itemName + "(s) have been received by the organization.")
                .build()
                .toString();

        sendMessage(json);
    }

    // el internal helper

    private void sendMessage(String jsonPayload) {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            MessageProducer producer = session.createProducer(notificationQueue);
            TextMessage message = session.createTextMessage(jsonPayload);
            producer.send(message);

            System.out.println("[NotificationSender] Message sent to queue: " + jsonPayload);

        } catch (JMSException e) {
            System.err.println("[NotificationSender] Failed to send JMS message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}