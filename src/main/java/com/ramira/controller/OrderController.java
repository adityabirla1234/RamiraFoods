package com.ramira.controller;

import com.ramira.dto.OrderRequestDto;
import com.ramira.dto.OrderResponseDto;
import com.ramira.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * POST /api/orders
 *
 * Accepts the checkout form payload, validates it, delegates to OrderService,
 * and returns the orderId to the browser immediately.
 *
 * The Telegram notification is dispatched asynchronously — this endpoint
 * never waits for it, so response time is always fast.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /* ── Main endpoint ─────────────────────────────────────────────────── */

    /**
     * Place a new order.
     * @param dto  validated request body from the checkout form
     * @return 201 Created + { orderId, message }
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponseDto> placeOrder(@Valid @RequestBody OrderRequestDto dto) {
        log.info("Received order from {} ({})", dto.getCustomerName(), dto.getCity());
        OrderResponseDto response = orderService.placeOrder(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /* ── Health check ─────────────────────────────────────────────────── */

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Ramira Foods Order API"));
    }

    /* ── Exception handlers ───────────────────────────────────────────── */

    /**
     * Bean validation failures (e.g. missing required field).
     * Returns 400 with a map of field → error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return Map.of(
                "error",  "Validation failed",
                "fields", fieldErrors
        );
    }

    /**
     * Any other unexpected server error.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGenericError(Exception ex) {
        log.error("Unhandled exception in OrderController", ex);
        return Map.of("error", "Internal server error. Please try again.");
    }
}
