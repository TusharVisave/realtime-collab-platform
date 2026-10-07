package com.collab.platform.service;

import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.dto.EditItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.repository.BoardItemRepository;
import com.collab.platform.repository.ConflictRecordRepository;
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

    private final BoardItemRepository itemRepository;
    private final ConflictRecordRepository conflictRepository;
    private final ConflictService conflictService;

    @Transactional
    public BoardItem createItem(CreateItemRequest req) {
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
