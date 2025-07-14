package com.chatapp.whatsapp.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 100, message = "Group name must be between 3 and 100 characters")
    private String name;

    @NotNull(message = "Creator ID is required")
    private Long creatorId;

    @NotNull(message = "Member list cannot be null")
    @Size(min = 1, message = "Group must have at least one other member besides the creator")
    private List<Long> memberIds;
}