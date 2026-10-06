package com.collab.platform.dto;

import com.collab.platform.model.BoardItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictReport {
    private String recordId;
    private String roomId;
    private String itemId;
    private String itemTitle;
    private String winningUser;
    private String rejectedUser;
    private Long winningVersion;
    private Long rejectedVersion;
    private Long winningTimestamp;
    private Long rejectedTimestamp;
    private BoardItem currentWinnerItem;
    private String resolutionReason;
}
