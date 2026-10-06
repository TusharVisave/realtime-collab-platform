package com.collab.platform.service;

import com.collab.platform.dto.ConflictReport;
import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.dto.EditItemRequest;
import com.collab.platform.dto.RoomSyncPayload;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ConflictRecord;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.model.Room;
import com.collab.platform.repository.BoardItemRepository;
import com.collab.platform.repository.ConflictRecordRepository;
import com.collab.platform.repository.RoomRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final RoomRepository roomRepository;
    private final BoardItemRepository itemRepository;
    private final ConflictRecordRepository conflictRepository;
    private final ConflictService conflictService;
    private final PresenceService presenceService;

    @PostConstruct
    public void initSeedData() {
        if (roomRepository.count() == 0) {
            log.info("Seeding initial collaboration rooms and board items...");
            Room roomEng = Room.builder()
                    .id("engineering-sync")
                    .name("Engineering Core Sync")
                    .description("Distributed systems, WebSocket concurrency & LWW consensus")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            roomRepository.save(roomEng);

            Room roomDesign = Room.builder()
                    .id("design-review")
                    .name("Product & Architecture Review")
                    .description("UI Glassmorphism, Presence HUD, and Realtime Experience")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            roomRepository.save(roomDesign);

            BoardItem item1 = BoardItem.builder()
                    .id("task-1")
                    .roomId("engineering-sync")
                    .title("Tune WebSocket Epoll Buffer Limits")
                    .description("Analyze TCP socket memory footprint and kernel file descriptors for 10k connections")
                    .status(ItemStatus.IN_PROGRESS)
                    .color("#3B82F6")
                    .version(1L)
                    .clientTimestamp(System.currentTimeMillis() - 60000)
                    .serverTimestamp(System.currentTimeMillis() - 60000)
                    .lastModifiedBy("System")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            BoardItem item2 = BoardItem.builder()
                    .id("task-2")
                    .roomId("engineering-sync")
                    .title("Implement STOMP Room Broadcast Scoping")
                    .description("Ensure messages sent to /topic/rooms/{id} are strictly isolated across tenants")
                    .status(ItemStatus.DONE)
                    .color("#10B981")
                    .version(1L)
                    .clientTimestamp(System.currentTimeMillis() - 50000)
                    .serverTimestamp(System.currentTimeMillis() - 50000)
                    .lastModifiedBy("System")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            BoardItem item3 = BoardItem.builder()
                    .id("task-3")
                    .roomId("engineering-sync")
                    .title("Simulate Near-Simultaneous LWW Race")
                    .description("Test dual-editor conflict handling: verify older edits get rejected deterministically")
                    .status(ItemStatus.TODO)
                    .color("#8B5CF6")
                    .version(1L)
                    .clientTimestamp(System.currentTimeMillis() - 40000)
                    .serverTimestamp(System.currentTimeMillis() - 40000)
                    .lastModifiedBy("System")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            itemRepository.saveAll(List.of(item1, item2, item3));
            log.info("Initialized default rooms and board tasks successfully.");
        }
    }

    @Transactional
    public Room getOrCreateRoom(String roomId, String name, String description) {
        return roomRepository.findById(roomId).orElseGet(() -> {
            Room newRoom = Room.builder()
                    .id(roomId)
                    .name((name != null && !name.isBlank()) ? name : "Room " + roomId)
                    .description(description != null ? description : "Realtime collaborative space")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            return roomRepository.save(newRoom);
        });
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public Optional<Room> getRoom(String roomId) {
        return roomRepository.findById(roomId);
    }

    @Transactional(readOnly = true)
    public RoomSyncPayload getRoomSyncSnapshot(String roomId) {
        Room room = getOrCreateRoom(roomId, null, null);
        List<BoardItem> items = itemRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        List<ConflictRecord> conflicts = conflictRepository.findTop15ByRoomIdOrderByCreatedAtDesc(roomId);
        var activeUsers = presenceService.getUsersInRoom(roomId);

        return RoomSyncPayload.builder()
                .room(room)
                .items(items)
                .activeUsers(activeUsers)
                .recentConflicts(conflicts)
                .serverTime(System.currentTimeMillis())
                .build();
    }

    @Transactional
    public BoardItem createItem(CreateItemRequest req) {
        getOrCreateRoom(req.getRoomId(), null, null);

        long now = System.currentTimeMillis();
        BoardItem item = BoardItem.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .roomId(req.getRoomId())
                .title(req.getTitle())
                .description(req.getDescription())
                .status(req.getStatus() != null ? req.getStatus() : ItemStatus.TODO)
                .color(req.getColor() != null ? req.getColor() : "#3B82F6")
                .version(1L)
                .clientTimestamp(now)
                .serverTimestamp(now)
                .lastModifiedBy(req.getSender() != null ? req.getSender() : "Anonymous")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return itemRepository.save(item);
    }

    @Transactional
    public ConflictService.ResolutionResult editItem(EditItemRequest req) {
        BoardItem stored = itemRepository.findByRoomIdAndId(req.getRoomId(), req.getId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + req.getId() + " in room: " + req.getRoomId()));

        return conflictService.resolveAndApplyEdit(stored, req);
    }

    @Transactional
    public BoardItem updateItemStatus(String roomId, String itemId, ItemStatus status, String sender) {
        BoardItem stored = itemRepository.findByRoomIdAndId(roomId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        stored.setStatus(status);
        stored.setLastModifiedBy(sender);
        stored.setVersion(stored.getVersion() + 1);
        stored.setServerTimestamp(System.currentTimeMillis());
        return itemRepository.save(stored);
    }

    @Transactional
    public boolean deleteItem(String roomId, String itemId) {
        Optional<BoardItem> item = itemRepository.findByRoomIdAndId(roomId, itemId);
        if (item.isPresent()) {
            itemRepository.deleteByRoomIdAndId(roomId, itemId);
            return true;
        }
        return false;
    }

    public List<BoardItem> getItems(String roomId) {
        return itemRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }
}
