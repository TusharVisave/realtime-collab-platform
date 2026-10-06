package com.collab.platform.service;

import com.collab.platform.dto.ConflictReport;
import com.collab.platform.dto.EditItemRequest;
import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ConflictRecord;
import com.collab.platform.repository.BoardItemRepository;
import com.collab.platform.repository.ConflictRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictService {

    private final BoardItemRepository itemRepository;
    private final ConflictRecordRepository conflictRecordRepository;

    public enum ResolutionType {
        CLEAN_UPDATE,
        LWW_OVERWRITE,
        LWW_REJECTED
    }

    public record ResolutionResult(
            ResolutionType type,
            BoardItem finalItem,
            ConflictReport conflictReport
    ) {}

    /**
     * Evaluates an edit request against the currently stored item using Last-Write-Wins (LWW).
     *
     * Concurrency / LWW Logic:
     * - Case 1: Sequential clean edit. baseVersion == stored.version.
     *   No concurrent modification occurred. Version advances by 1.
     *
     * - Case 2: Concurrent edit detected. stored.version > baseVersion.
     *   Another client committed an edit while this client was working.
     *   Compare request.clientTimestamp with stored.clientTimestamp:
     *     - If request.clientTimestamp > stored.clientTimestamp:
     *       The incoming edit is newer in wall-clock time -> OVERWRITE.
     *       Stored state is overwritten with incoming edits, version increments,
     *       and a conflict audit is recorded naming the winner and loser.
     *     - If request.clientTimestamp <= stored.clientTimestamp:
     *       The incoming edit is older -> REJECTED.
     *       The stored item remains unchanged, and a conflict audit is recorded
     *       so the rejecting client receives current winner state to reconcile.
     */
    @Transactional
    public ResolutionResult resolveAndApplyEdit(BoardItem stored, EditItemRequest request) {
        long currentVersion = (stored.getVersion() != null) ? stored.getVersion() : 1L;
        long baseVersion = (request.getBaseVersion() != null) ? request.getBaseVersion() : currentVersion;
        long clientTs = (request.getClientTimestamp() != null && request.getClientTimestamp() > 0)
                ? request.getClientTimestamp()
                : System.currentTimeMillis();

        long storedClientTs = (stored.getClientTimestamp() != null) ? stored.getClientTimestamp() : 0L;

        // Check if concurrent edit occurred
        if (currentVersion == baseVersion) {
            // Clean sequential edit
            applyUpdates(stored, request, currentVersion + 1, clientTs);
            BoardItem saved = itemRepository.save(stored);
            return new ResolutionResult(ResolutionType.CLEAN_UPDATE, saved, null);
        }

        // Concurrent conflict occurred!
        log.warn("Conflict detected for item [{}] in room [{}]. Stored v{} vs Request baseV{}",
                stored.getId(), request.getRoomId(), currentVersion, baseVersion);

        if (clientTs > storedClientTs) {
            // LWW WIN: Incoming edit is newer than stored write
            long winningVersion = currentVersion + 1;
            String reason = String.format("LWW Overwrite: incoming edit timestamp (%d) is newer than stored edit (%d) by %dms",
                    clientTs, storedClientTs, (clientTs - storedClientTs));

            ConflictRecord record = ConflictRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .roomId(request.getRoomId())
                    .itemId(stored.getId())
                    .itemTitle(request.getTitle() != null ? request.getTitle() : stored.getTitle())
                    .winningUser(request.getSender())
                    .rejectedUser(stored.getLastModifiedBy())
                    .winningVersion(winningVersion)
                    .rejectedVersion(currentVersion)
                    .winningTimestamp(clientTs)
                    .rejectedTimestamp(storedClientTs)
                    .resolutionReason(reason)
                    .createdAt(Instant.now())
                    .build();

            conflictRecordRepository.save(record);

            applyUpdates(stored, request, winningVersion, clientTs);
            BoardItem saved = itemRepository.save(stored);

            ConflictReport report = ConflictReport.builder()
                    .recordId(record.getId())
                    .roomId(record.getRoomId())
                    .itemId(record.getItemId())
                    .itemTitle(record.getItemTitle())
                    .winningUser(record.getWinningUser())
                    .rejectedUser(record.getRejectedUser())
                    .winningVersion(record.getWinningVersion())
                    .rejectedVersion(record.getRejectedVersion())
                    .winningTimestamp(record.getWinningTimestamp())
                    .rejectedTimestamp(record.getRejectedTimestamp())
                    .currentWinnerItem(saved)
                    .resolutionReason(reason)
                    .build();

            return new ResolutionResult(ResolutionType.LWW_OVERWRITE, saved, report);

        } else {
            // LWW LOSS: Incoming edit is older than or equal to stored write -> REJECT
            String reason = String.format("LWW Rejected: incoming edit timestamp (%d) is older than stored edit (%d) by %dms",
                    clientTs, storedClientTs, (storedClientTs - clientTs));

            ConflictRecord record = ConflictRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .roomId(request.getRoomId())
                    .itemId(stored.getId())
                    .itemTitle(stored.getTitle())
                    .winningUser(stored.getLastModifiedBy())
                    .rejectedUser(request.getSender())
                    .winningVersion(currentVersion)
                    .rejectedVersion(baseVersion)
                    .winningTimestamp(storedClientTs)
                    .rejectedTimestamp(clientTs)
                    .resolutionReason(reason)
                    .createdAt(Instant.now())
                    .build();

            conflictRecordRepository.save(record);

            ConflictReport report = ConflictReport.builder()
                    .recordId(record.getId())
                    .roomId(record.getRoomId())
                    .itemId(record.getItemId())
                    .itemTitle(record.getItemTitle())
                    .winningUser(record.getWinningUser())
                    .rejectedUser(record.getRejectedUser())
                    .winningVersion(record.getWinningVersion())
                    .rejectedVersion(record.getRejectedVersion())
                    .winningTimestamp(record.getWinningTimestamp())
                    .rejectedTimestamp(record.getRejectedTimestamp())
                    .currentWinnerItem(stored)
                    .resolutionReason(reason)
                    .build();

            return new ResolutionResult(ResolutionType.LWW_REJECTED, stored, report);
        }
    }

    private void applyUpdates(BoardItem item, EditItemRequest request, long newVersion, long clientTs) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            item.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        if (request.getColor() != null && !request.getColor().isBlank()) {
            item.setColor(request.getColor());
        }
        item.setLastModifiedBy(request.getSender());
        item.setClientTimestamp(clientTs);
        item.setServerTimestamp(System.currentTimeMillis());
        item.setVersion(newVersion);
    }
}
