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
public class CreateItemRequest {
    @NotBlank
    private String roomId;

    @NotBlank
    private String title;

    private String description;
    private ItemStatus status;
    private String color;

    @NotBlank
    private String sender;
}
