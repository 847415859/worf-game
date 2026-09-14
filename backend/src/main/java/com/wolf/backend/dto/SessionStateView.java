package com.wolf.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class SessionStateView {
    private Long sessionId;
    private Long roomId;
    private int dayCount;
    private String phase;
    private String status;
    private String winnerCamp;
    private Long sheriffPlayerId;
    private boolean paused;
    private List<PlayerView> players;
    private List<EventView> recentEvents;

    /** 仅当请求带上playerId时返回，帮助前端知道"我是谁、要不要行动" */
    private Long myPlayerId;
    private String myInGameRole;
    private PendingActionView pendingAction;
}
