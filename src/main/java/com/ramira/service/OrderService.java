package com.ramira.service;

import com.ramira.dto.OrderItemDto;
import com.ramira.dto.OrderRequestDto;
import com.ramira.dto.OrderResponseDto;
import com.ramira.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core order service.
 *
 * Responsibilities:
 *  1. Map validated DTO → domain Order
 *  2. Generate a human-readable order ID
 *  3. "Persist" the order (in-memory map shown here; swap for a DB in prod)
 *  4. Hand off to TelegramNotificationService (fire-and-forget, async)
 *  5. Return the orderId to the controller immediately
 *
 * The HTTP response is returned to the browser BEFORE the Telegram
 * notification is sent — so the API is always fast regardless of
 * Telegram's response time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final TelegramNotificationService telegramService;

    /**
     * In-memory order store.
     * Replace with Spring Data JPA repository for production:
     *   @Autowired OrderRepository orderRepository;
     *   orderRepository.save(order);
     */
    private final ConcurrentHashMap<String, Order> orderStore = new ConcurrentHashMap<>();

    public OrderResponseDto placeOrder(OrderRequestDto dto) {
        // 1. Generate order ID
        String orderId = generateOrderId();

        // 2. Recalculate server-side totals (never trust client-side values)
        double subtotal = dto.getItems().stream()
                .mapToDouble(i -> i.getUnitPrice() * i.getQty())
                .sum();
        double shipping = subtotal >= 499 ? 0 : 49;
        double total    = subtotal + shipping;

        // 3. Build domain object
        Order order = Order.builder()
                .orderId(orderId)
                .customerName(dto.getCustomerName())
                .contactNumber(dto.getContactNumber())
                .altContactNumber(dto.getAltContactNumber())
                .fullAddress(dto.getFullAddress())
                .pincode(dto.getPincode())
                .city(dto.getCity())
                .state(dto.getState())
                .items(dto.getItems())
                .subtotal(subtotal)
                .shipping(shipping)
                .total(total)
                .orderedAt(dto.getOrderedAt() != null ? dto.getOrderedAt() : Instant.now())
                .receivedAt(Instant.now())
                .build();

        // 4. Persist (in-memory; replace with DB call)
        orderStore.put(orderId, order);
        log.info("Order {} saved. Total orders in store: {}", orderId, orderStore.size());

        // 5. Enqueue Telegram notification (non-blocking — returns immediately)
        telegramService.sendOrderNotification(order);

        // 6. Return response to browser right away
        return new OrderResponseDto(orderId, "Order placed successfully! We'll contact you shortly.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Generates a readable order reference like "RF-20240512-A3F9".
     * Adjust the prefix or format to your preference.
     */
    private String generateOrderId() {
        String datePart = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.of("Asia/Kolkata"))
                .format(Instant.now());
        String randPart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "RF-" + datePart + "-" + randPart;
    }
}
