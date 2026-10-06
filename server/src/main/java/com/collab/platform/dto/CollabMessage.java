package com.collab.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollabMessage {
    private CollabAction action;
    private String roomId;
    private String sender;
    private Object payload;
    private Long timestamp;

    public static CollabMessage of(CollabAction action, String roomId, String sender, Object payload) {
        return CollabMessage.builder()
                .action(action)
                .roomId(roomId)
                .sender(sender)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
