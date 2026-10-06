package com.collab.platform.controller;

import com.collab.platform.dto.CollabAction;
import com.collab.platform.dto.CollabMessage;
import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.service.BoardService;
import com.collab.platform.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BoardWebSocketController {

    private final BoardService boardService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Join Room and register presence
     */
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId,
                         @Payload Map<String, String> payload,
                         SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = payload.getOrDefault("username", "Anonymous");
        String color = payload.get("color");

        presenceService.registerUser(sessionId, roomId, username, color);

        // Broadcast presence update strictly to room topic
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                CollabMessage.of(CollabAction.PRESENCE_SYNC, roomId, username, presenceService.getUsersInRoom(roomId)));

        log.info("User {} joined room {}", username, roomId);
    }

    /**
     * Create Item in Room
     */
    @MessageMapping("/room/{roomId}/create")
    public void createItem(@DestinationVariable String roomId,
                           @Payload CreateItemRequest request) {
        request.setRoomId(roomId);
        BoardItem created = boardService.createItem(request);

        log.info("Item created [{}] in room [{}] by {}", created.getId(), roomId, created.getLastModifiedBy());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                CollabMessage.of(CollabAction.ITEM_CREATED, roomId, created.getLastModifiedBy(), created));
    }

    /**
     * Live typing / active item editing indicator
     */
    @MessageMapping("/room/{roomId}/typing")
    public void reportTyping(@DestinationVariable String roomId,
                             @Payload Map<String, String> payload,
                             SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String itemId = payload.get("itemId");
        String username = payload.getOrDefault("username", "Anonymous");

        presenceService.updateEditingItem(sessionId, itemId);

        messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                CollabMessage.of(CollabAction.PRESENCE_TYPING, roomId, username,
                        Map.of("username", username, "itemId", (itemId != null ? itemId : ""))));
    }
}
