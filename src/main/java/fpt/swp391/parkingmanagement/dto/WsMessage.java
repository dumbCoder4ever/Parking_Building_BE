package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WsMessage<T> {

    /** Loại sự kiện: USER_UPDATED, USER_STATUS_CHANGED, PARKING_SLOT_UPDATED, ... */
    private String event;

    private T payload;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> WsMessage<T> of(String event, T payload) {
        return WsMessage.<T>builder()
                .event(event)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
