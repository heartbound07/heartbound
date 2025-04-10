package com.app.heartbound.dto.riot;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class RiotMatchlistDto {
    private String puuid;
    private List<RiotMatchlistEntryDto> history;
} 