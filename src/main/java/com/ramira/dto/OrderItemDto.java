package com.ramira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mirrors a single cart entry sent by the frontend:
 * { productId, name, weight, qty, unitPrice, subtotal }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderItemDto {

    @NotBlank(message = "productId is required")
    private String productId;

    @NotBlank(message = "Item name is required")
    private String name;

    private int weight;      // grams

    @NotNull @Min(1)
    private Integer qty;

    @NotNull @Min(0)
    private Double unitPrice;

    private Double subtotal;
}
