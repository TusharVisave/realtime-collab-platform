package com.collab.platform.dto;

import com.collab.platform.model.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditItemRequest {
    @NotBlank
    private String id;

    @NotBlank
    private String roomId;

    private String title;
    private String description;
    private ItemStatus status;
    private String color;

    /**
     * The version number that the user had when they opened or made the edit.
     * Used by the server to determine if a concurrent edit happened.
     */
    private Long baseVersion;

    /**
     * Timestamp on client machine when edit occurred.
     */
    private Long clientTimestamp;

    @NotBlank
    private String sender;
}
