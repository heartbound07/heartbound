package com.app.heartbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateTradeDto {

    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotEmpty(message = "You must offer at least one item")
    private List<UUID> offeredItemInstanceIds;

} 