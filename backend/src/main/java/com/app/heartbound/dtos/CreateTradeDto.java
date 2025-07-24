package com.app.heartbound.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateTradeDto {

    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotEmpty(message = "You must offer at least one item")
    private List<TradeItemDto> offeredItems;

    @Data
    public static class TradeItemDto {

        @NotNull(message = "Item ID is required")
        private UUID itemId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        public TradeItemDto(UUID itemId, Integer quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
} 