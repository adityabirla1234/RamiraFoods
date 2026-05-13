package com.ramira.model;

import com.ramira.dto.OrderItemDto;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Internal domain object created after validation.
 * In a production system you'd persist this to a database
 * (e.g. Spring Data JPA + PostgreSQL/MySQL).
 * For now it lives in memory and is passed to the notification queue.
 */
@Data
@Builder
public class Order {

    private String orderId;

    /* Customer */
    private String customerName;
    private String contactNumber;
    private String altContactNumber;
    private String fullAddress;
    private String pincode;
    private String city;
    private String state;

    /* Items & totals */
    private List<OrderItemDto> items;
    private double subtotal;
    private double shipping;
    private double total;

    /* Metadata */
    private Instant orderedAt;
    private Instant receivedAt;     // server-side timestamp
}
