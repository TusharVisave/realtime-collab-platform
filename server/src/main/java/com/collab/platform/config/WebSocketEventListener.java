package com.collab.platform.config;

import com.collab.platform.dto.CollabAction;
import com.collab.platform.dto.CollabMessage;
import com.collab.platform.dto.PresenceUser;
import com.collab.platform.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        log.info("WebSocket disconnect detected for session: {}", sessionId);

        presenceService.removeSession(sessionId).ifPresent(result -> {
            String roomId = result.roomId();
            PresenceUser user = result.user();

            List<PresenceUser> updatedUsers = presenceService.getUsersInRoom(roomId);
            log.info("Broadcasting updated presence for room {} after {} left. Remaining users: {}",
                    roomId, user.getUsername(), updatedUsers.size());

            CollabMessage presenceMsg = CollabMessage.of(
                    CollabAction.PRESENCE_SYNC,
                    roomId,
                    "System",
                    updatedUsers
            );

            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, presenceMsg);
        });
    }
}
