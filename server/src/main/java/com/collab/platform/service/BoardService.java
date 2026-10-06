package com.collab.platform.service;

import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.repository.BoardItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardItemRepository itemRepository;

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

    public List<BoardItem> getItems(String roomId) {
        return itemRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
    }
}
