package com.collab.platform.controller;

import com.collab.platform.dto.*;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.service.BoardService;
import com.collab.platform.service.ConflictService;
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
     * Edit Item with Last-Write-Wins (LWW) conflict handling
     */
    @MessageMapping("/room/{roomId}/edit")
    public void editItem(@DestinationVariable String roomId,
                         @Payload EditItemRequest request) {
        request.setRoomId(roomId);
        ConflictService.ResolutionResult result = boardService.editItem(request);

        if (result.type() == ConflictService.ResolutionType.CLEAN_UPDATE) {
            log.info("Clean update for item [{}] in room [{}], new v{}",
                    result.finalItem().getId(), roomId, result.finalItem().getVersion());
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                    CollabMessage.of(CollabAction.ITEM_UPDATED, roomId, request.getSender(), result.finalItem()));

        } else if (result.type() == ConflictService.ResolutionType.LWW_OVERWRITE) {
            log.warn("LWW Overwrite applied for item [{}] in room [{}]. Winner: {}",
                    result.finalItem().getId(), roomId, result.conflictReport().getWinningUser());

            // 1. Broadcast the updated item
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                    CollabMessage.of(CollabAction.ITEM_UPDATED, roomId, request.getSender(), result.finalItem()));

            // 2. Broadcast the conflict report explaining why and who won
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                    CollabMessage.of(CollabAction.CONFLICT_DETECTED, roomId, "System", result.conflictReport()));

        } else {
            // LWW_REJECTED
            log.warn("LWW Rejection for item [{}] in room [{}]. Rejected: {}, Winner: {}",
                    result.finalItem().getId(), roomId, result.conflictReport().getRejectedUser(), result.conflictReport().getWinningUser());

            // Broadcast conflict notification with latest winning state
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                    CollabMessage.of(CollabAction.CONFLICT_DETECTED, roomId, "System", result.conflictReport()));
        }
    }

    /**
     * Quick status transition (TODO <-> IN_PROGRESS <-> DONE)
     */
    @MessageMapping("/room/{roomId}/status")
    public void updateStatus(@DestinationVariable String roomId,
                             @Payload Map<String, String> payload) {
        String itemId = payload.get("itemId");
        String statusStr = payload.get("status");
        String sender = payload.getOrDefault("sender", "Anonymous");

        ItemStatus status = ItemStatus.valueOf(statusStr);
        BoardItem updated = boardService.updateItemStatus(roomId, itemId, status, sender);

        messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                CollabMessage.of(CollabAction.ITEM_STATUS_CHANGED, roomId, sender, updated));
    }

    /**
     * Delete Item in Room
     */
    @MessageMapping("/room/{roomId}/delete")
    public void deleteItem(@DestinationVariable String roomId,
                           @Payload Map<String, String> payload) {
        String itemId = payload.get("itemId");
        String sender = payload.getOrDefault("sender", "Anonymous");

        boolean deleted = boardService.deleteItem(roomId, itemId);
        if (deleted) {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId,
                    CollabMessage.of(CollabAction.ITEM_DELETED, roomId, sender, Map.of("itemId", itemId)));
        }
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
