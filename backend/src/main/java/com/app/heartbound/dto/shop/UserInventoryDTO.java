package com.app.heartbound.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInventoryDTO {
    @NotNull
    @NotEmpty(message = "User ID cannot be empty")
    private String userId;
    
    @Valid
    private Set<ShopDTO> items;
}
