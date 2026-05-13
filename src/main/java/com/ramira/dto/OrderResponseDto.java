package com.ramira.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Returned to the browser immediately after the order is accepted.
 * The frontend reads `data.orderId` to display in the success modal.
 */
@Data
@AllArgsConstructor
public class OrderResponseDto {

    /** Human-readable order reference, e.g. "RF-20240512-A3F9" */
    private String orderId;

    private String message;
}
