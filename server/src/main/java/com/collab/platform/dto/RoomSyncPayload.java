package com.collab.platform.dto;

import com.collab.platform.model.BoardItem;
import com.collab.platform.model.ConflictRecord;
import com.collab.platform.model.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomSyncPayload {
    private Room room;
    private List<BoardItem> items;
    private List<PresenceUser> activeUsers;
    private List<ConflictRecord> recentConflicts;
    private Long serverTime;
}
