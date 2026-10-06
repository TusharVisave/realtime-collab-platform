package com.collab.platform.controller;

import com.collab.platform.dto.CollabAction;
import com.collab.platform.dto.CollabMessage;
import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.service.BoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BoardWebSocketController {

    private final BoardService boardService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Create Item in Room and broadcast to room topic
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
}
