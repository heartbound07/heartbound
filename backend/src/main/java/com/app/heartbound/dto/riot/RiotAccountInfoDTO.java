package com.app.heartbound.dto.riot;

import lombok.Data;

@Data
public class RiotAccountInfoDTO {
    private String puuid;
    private String gameName;
    private String tagLine;
    // Add other fields if needed from the Riot API response
}
