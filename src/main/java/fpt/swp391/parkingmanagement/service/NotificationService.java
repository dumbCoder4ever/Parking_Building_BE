package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.PaymentResponseDTO;
import fpt.swp391.parkingmanagement.dto.WsMessage;
import fpt.swp391.parkingmanagement.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service gửi realtime notification qua WebSocket (STOMP).
 *
 * Các topic:
 *  - /topic/users          → broadcast toàn bộ client (admin dashboard)
 *  - /user/{username}/queue/notifications → gửi riêng cho từng user
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Broadcast tới tất cả admin đang subscribe /topic/users */
    public <T> void broadcastToAdmins(String event, T payload) {
        messagingTemplate.convertAndSend("/topic/users", WsMessage.of(event, payload));
    }

    /** Gửi notification riêng cho một user theo username */
    public <T> void sendToUser(String username, String event, T payload) {
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/notifications",
                WsMessage.of(event, payload)
        );
    }

    /** Broadcast trạng thái parking (cho màn hình tổng quan) */
    public <T> void broadcastParkingUpdate(String event, T payload) {
        messagingTemplate.convertAndSend("/topic/parking/status", WsMessage.of(event, payload));
    }

    public void sendPaymentInitiationToDriver(User driver, PaymentResponseDTO paymentResponse) {
        sendToUser(driver.getUsername(), "PAYMENT_INITIATED", paymentResponse);
    }

    public void sendPaymentSuccessToDriver(User driver, PaymentResponseDTO paymentResponse) {
        sendToUser(driver.getUsername(), "PAYMENT_PAID", paymentResponse);
    }
}
