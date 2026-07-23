package fpt.swp391.parkingmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.swp391.parkingmanagement.dto.WsMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service gửi realtime notification qua WebSocket (STOMP).
 *
 * Các topic:
 *  - /topic/users                    → broadcast toàn bộ client (admin dashboard)
 *  - /user/{username}/queue/notifications → gửi riêng cho từng user (driver)
 *  - /topic/buildings/{buildingId}/notifications → gửi cho staff của building
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

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

    /** Broadcast tới staff của một building */
    public <T> void sendToStaffBuilding(String buildingId, String event, T payload) {
        try {
            String dest = "/topic/buildings/" + buildingId + "/notifications";
            messagingTemplate.convertAndSend(dest, WsMessage.of(event, payload));
        } catch (Exception e) {
            log.warn("Failed to send notification to building {}: {}", buildingId, e.getMessage());
        }
    }
}
