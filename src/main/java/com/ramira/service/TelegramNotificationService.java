package com.ramira.service;

import com.ramira.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Sends a formatted Telegram message for every new order.
 *
 * The method is annotated @Async("notificationExecutor") so it runs
 * on the dedicated notification thread pool and never blocks the
 * HTTP request thread that accepted the order.
 *
 * Retry strategy: if Telegram returns a transient error (5xx / timeout)
 * we retry up to 3 times with a short delay before giving up and logging
 * the failure. We never lose the order itself — it is already accepted and
 * persisted (or at minimum in memory) before this method is called.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private static final String TELEGRAM_API = "https://api.telegram.org";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1_500;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat.id}")
    private Long chatId;

    private final WebClient webClient;

    /**
     * Formats the order as a rich Telegram message and sends it asynchronously.
     * Runs on the "notificationExecutor" pool, NOT the HTTP thread.
     */
    @Async("notificationExecutor")
    public void sendOrderNotification(Order order) {
        String message = buildMessage(order);
        log.debug("Queuing Telegram notification for order {}", order.getOrderId());

        sendWithRetry(message, 1);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void sendWithRetry(String message, int attempt) {
        try {
            String url = TELEGRAM_API + "/bot" + botToken + "/sendMessage";

            Map<String, Object> body = Map.of(
                    "chat_id",    chatId,
                    "text",       message,
                    "parse_mode", "HTML"
            );

            webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body2 -> Mono.error(
                                            new RuntimeException("Telegram 4xx: " + body2)))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body2 -> Mono.error(
                                            new RuntimeException("Telegram 5xx: " + body2)))
                    )
                    .bodyToMono(String.class)
                    .block();  // block is fine here — we are already on a worker thread

            log.info("✅ Telegram notification sent (attempt {})", attempt);

        } catch (Exception ex) {
            log.warn("⚠️  Telegram attempt {}/{} failed: {}", attempt, MAX_RETRIES, ex.getMessage());

            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                sendWithRetry(message, attempt + 1);
            } else {
                log.error("❌ All {} Telegram attempts exhausted. Message lost from Telegram " +
                        "(order is safe in server logs): {}", MAX_RETRIES, message);
            }
        }
    }

    private String buildMessage(Order order) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss z");
        String receivedAt = ZonedDateTime
                .ofInstant(order.getReceivedAt(), ZoneId.of("Asia/Kolkata"))
                .format(fmt);

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 <b>NEW ORDER — Ramira Foods</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("📋 <b>Order ID:</b> %s\n", order.getOrderId()));
        sb.append(String.format("🕒 <b>Received:</b> %s\n\n", receivedAt));

        sb.append("👤 <b>CUSTOMER</b>\n");
        sb.append(String.format("  Name    : %s\n", esc(order.getCustomerName())));
        sb.append(String.format("  Phone   : %s\n", esc(order.getContactNumber())));
        if (order.getAltContactNumber() != null && !order.getAltContactNumber().isBlank()) {
            sb.append(String.format("  Alt Ph. : %s\n", esc(order.getAltContactNumber())));
        }
        sb.append("\n");

        sb.append("📍 <b>DELIVERY ADDRESS</b>\n");
        sb.append(String.format("  %s\n", esc(order.getFullAddress())));
        sb.append(String.format("  %s – %s\n", esc(order.getCity()), esc(order.getPincode())));
        sb.append(String.format("  %s\n\n", esc(order.getState())));

        sb.append("🧾 <b>ITEMS ORDERED</b>\n");
        for (var item : order.getItems()) {
            sb.append(String.format("  • %s (%dg) × %d = ₹%.0f\n",
                    esc(item.getName()), item.getWeight(), item.getQty(), item.getSubtotal()));
        }
        sb.append("\n");

        sb.append(String.format("  Subtotal : ₹%.0f\n", order.getSubtotal()));
        if (order.getShipping() == 0) {
            sb.append("  Shipping : FREE 🎉\n");
        } else {
            sb.append(String.format("  Shipping : ₹%.0f\n", order.getShipping()));
        }
        sb.append(String.format("  <b>TOTAL    : ₹%.0f</b>\n", order.getTotal()));
        sb.append("━━━━━━━━━━━━━━━━━━━━━━");

        return sb.toString();
    }

    /** Escape HTML special chars for Telegram parse_mode=HTML */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
