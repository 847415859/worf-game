package com.wolf.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventView {
    private Long id;
    private int day;
    private String phase;
    private Long actorPlayerId;
    private String actorName;
    private String eventType;
    private Long targetPlayerId;
    private String targetName;
    private String claimedRole;
    private String content;
    private String visibleScope;
    private LocalDateTime createdAt;
}
