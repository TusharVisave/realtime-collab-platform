package com.collab.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceUser {
    private String sessionId;
    private String username;
    private String color;
    private Long joinedAt;
    private String currentEditingItemId;
}
