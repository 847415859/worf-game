package com.wolf.backend.ai;

import lombok.Data;

@Data
public class AiDecideResponse {
    private String content;
    private Long targetPlayerId;
    private String claimedRole;
    private Boolean decisionYes;
    private String innerMonologue;
}
