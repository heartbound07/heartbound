package com.app.heartbound.dto.riot;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class RiotMatchDto {
    private RiotMatchInfoDto matchInfo;
    private List<RiotPlayerDto> players;
    // Add teams, roundResults etc. if needed
} 