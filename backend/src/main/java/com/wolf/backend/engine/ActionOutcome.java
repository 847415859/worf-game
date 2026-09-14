package com.wolf.backend.engine;

import lombok.Data;

@Data
public class ActionOutcome {
    private String content;
    private Long targetPlayerId;
    private String claimedRole;
    private Boolean decisionYes;
    private String innerMonologue;
    private boolean fallback;
}
