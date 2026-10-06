package com.collab.platform.controller;

import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.dto.RoomSyncPayload;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.Room;
import com.collab.platform.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RoomRestController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(boardService.getAllRooms());
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomSyncPayload> getRoomSnapshot(@PathVariable String roomId) {
        return ResponseEntity.ok(boardService.getRoomSyncSnapshot(roomId));
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Map<String, String> body) {
        String id = body.getOrDefault("id", "room-" + System.currentTimeMillis());
        String name = body.getOrDefault("name", "New Collab Room");
        String desc = body.getOrDefault("description", "");
        return ResponseEntity.ok(boardService.getOrCreateRoom(id, name, desc));
    }

    @GetMapping("/{roomId}/items")
    public ResponseEntity<List<BoardItem>> getRoomItems(@PathVariable String roomId) {
        return ResponseEntity.ok(boardService.getItems(roomId));
    }

    @PostMapping("/{roomId}/items")
    public ResponseEntity<BoardItem> createItemRest(@PathVariable String roomId, @RequestBody CreateItemRequest req) {
        req.setRoomId(roomId);
        return ResponseEntity.ok(boardService.createItem(req));
    }
}
