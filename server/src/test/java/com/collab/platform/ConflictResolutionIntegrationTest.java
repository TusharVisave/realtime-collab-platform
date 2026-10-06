package com.collab.platform;

import com.collab.platform.dto.CreateItemRequest;
import com.collab.platform.dto.EditItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ConflictRecord;
import com.collab.platform.model.ItemStatus;
import com.collab.platform.repository.BoardItemRepository;
import com.collab.platform.repository.ConflictRecordRepository;
import com.collab.platform.service.BoardService;
import com.collab.platform.service.ConflictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ConflictResolutionIntegrationTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardItemRepository itemRepository;

    @Autowired
    private ConflictRecordRepository conflictRepository;

    @Test
    @DisplayName("Conflict test: simulate near-simultaneous edits, confirm Last-Write-Wins is deterministic, auditable, and explainable")
    void testDeterministicLastWriteWins() {
        String roomId = "conflict-room-test";

        // Step 1: Create initial item
        CreateItemRequest createReq = CreateItemRequest.builder()
                .roomId(roomId)
                .title("Initial Draft Title")
                .description("Initial Content")
                .status(ItemStatus.TODO)
                .sender("Alice")
                .build();

        BoardItem item = boardService.createItem(createReq);
        long initialVersion = item.getVersion(); // 1L
        String itemId = item.getId();

        // Step 2: User 1 applies an edit based on initialVersion (timestamp T = 100,000)
        long t1 = 100_000L;
        EditItemRequest editUser1 = EditItemRequest.builder()
                .id(itemId)
                .roomId(roomId)
                .title("Alice's Refined Title")
                .description("Alice's changes")
                .status(ItemStatus.IN_PROGRESS)
                .baseVersion(initialVersion)
                .clientTimestamp(t1)
                .sender("Alice")
                .build();

        ConflictService.ResolutionResult result1 = boardService.editItem(editUser1);
        assertThat(result1.type()).isEqualTo(ConflictService.ResolutionType.CLEAN_UPDATE);
        assertThat(result1.finalItem().getVersion()).isEqualTo(2L);
        assertThat(result1.finalItem().getTitle()).isEqualTo("Alice's Refined Title");
        assertThat(result1.conflictReport()).isNull();

        // Step 3: Concurrent edit by User 2 (Bob) based on initialVersion (baseVersion = 1),
        // but Bob made the edit slightly AFTER Alice (timestamp T = 100,500)
        long t2 = 100_500L;
        EditItemRequest editUser2 = EditItemRequest.builder()
                .id(itemId)
                .roomId(roomId)
                .title("Bob's High Priority Title")
                .description("Bob's concurrent edits")
                .status(ItemStatus.DONE)
                .baseVersion(initialVersion) // Bob started from v1, unaware Alice committed v2
                .clientTimestamp(t2)
                .sender("Bob")
                .build();

        ConflictService.ResolutionResult result2 = boardService.editItem(editUser2);

        // Verification: Bob's timestamp (100,500) > Alice's timestamp (100,000)
        // Under LWW policy: Bob's newer write wins and overwrites Alice's state
        assertThat(result2.type()).isEqualTo(ConflictService.ResolutionType.LWW_OVERWRITE);
        assertThat(result2.finalItem().getTitle()).isEqualTo("Bob's High Priority Title");
        assertThat(result2.finalItem().getVersion()).isEqualTo(3L);
        assertThat(result2.finalItem().getStatus()).isEqualTo(ItemStatus.DONE);

        // Verify Conflict Report and Audit Trail
        assertThat(result2.conflictReport()).isNotNull();
        assertThat(result2.conflictReport().getWinningUser()).isEqualTo("Bob");
        assertThat(result2.conflictReport().getRejectedUser()).isEqualTo("Alice");
        assertThat(result2.conflictReport().getWinningTimestamp()).isEqualTo(t2);
        assertThat(result2.conflictReport().getRejectedTimestamp()).isEqualTo(t1);
        assertThat(result2.conflictReport().getResolutionReason())
                .contains("newer than stored edit")
                .contains("by 500ms");

        // Step 4: Out-of-order delayed edit from User 3 (Charlie), who sent an edit with an older timestamp (T = 90,000)
        long t3 = 90_000L;
        EditItemRequest editUser3 = EditItemRequest.builder()
                .id(itemId)
                .roomId(roomId)
                .title("Charlie's Stale Title")
                .description("Stale packet from an old disconnected client")
                .status(ItemStatus.TODO)
                .baseVersion(initialVersion)
                .clientTimestamp(t3)
                .sender("Charlie")
                .build();

        ConflictService.ResolutionResult result3 = boardService.editItem(editUser3);

        // Verification: Charlie's timestamp (90,000) < Bob's timestamp (100,500)
        // Under LWW policy: Charlie's stale write is REJECTED
        assertThat(result3.type()).isEqualTo(ConflictService.ResolutionType.LWW_REJECTED);
        assertThat(result3.finalItem().getTitle()).isEqualTo("Bob's High Priority Title"); // remains unchanged!
        assertThat(result3.finalItem().getVersion()).isEqualTo(3L);

        // Verify Rejection Report
        assertThat(result3.conflictReport()).isNotNull();
        assertThat(result3.conflictReport().getWinningUser()).isEqualTo("Bob");
        assertThat(result3.conflictReport().getRejectedUser()).isEqualTo("Charlie");
        assertThat(result3.conflictReport().getResolutionReason()).contains("older than stored edit");

        // Verify database audit records exist
        List<ConflictRecord> audit = conflictRepository.findTop15ByRoomIdOrderByCreatedAtDesc(roomId);
        assertThat(audit).hasSizeGreaterThanOrEqualTo(2);
    }
}
