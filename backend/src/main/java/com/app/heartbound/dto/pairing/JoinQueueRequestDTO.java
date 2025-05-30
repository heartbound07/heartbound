package com.app.heartbound.dto.pairing;

import com.app.heartbound.enums.Rank;
import com.app.heartbound.enums.Region;
import com.app.heartbound.enums.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinQueueRequestDTO {

    @NotBlank(message = "User ID is required")
    private String userId;

    @Min(value = 18, message = "Age must be at least 18")
    @Max(value = 99, message = "Age must not exceed 99")
    private int age;

    @NotNull(message = "Region is required")
    private Region region;

    @NotNull(message = "Rank is required")
    private Rank rank;

    @NotNull(message = "Gender is required")
    private Gender gender;
} 