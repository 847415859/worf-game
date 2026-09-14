package com.wolf.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后端 GameEngine 调用 NestJS AI Agent 编排服务的请求契约（内部接口，双端自定义，
 * 对应需求文档8.1节System Prompt组装结构 与 8.3节两段式决策链）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDecideRequest {
    private CharacterInfo character;
    private GameContext gameContext;
    private Task task;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CharacterInfo {
        private Long characterId;
        private String name;
        private String backstory;
        private Object personalityVector; // 原样透传JSON对象
        private Object speechStyle;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SelfInfo {
        private Long playerId;
        private int seatNo;
        private String inGameRole;
        private String camp;
        private boolean sheriff;
        private boolean alive;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlayerInfo {
        private Long playerId;
        private int seatNo;
        private String displayName;
        private boolean alive;
        private boolean sheriff;
        /** 仅狼人互相可见队友身份时才会带上，见8.4节信息隔离 */
        private String knownCamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EventWindowItem {
        private int day;
        private String phase;
        private String actorName;
        private String eventType;
        private String claimedRole;
        private String targetName;
        private String content;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GameContext {
        private Long sessionId;
        private int day;
        private String phase;
        private SelfInfo self;
        private List<PlayerInfo> players;
        private List<EventWindowItem> eventWindow;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CandidateTarget {
        private Long playerId;
        private String displayName;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Task {
        /** speech/vote/night_action/last_words/sheriff_campaign_speech/sheriff_vote/pk_speech/badge_transfer */
        private String actionType;
        private String instruction;
        private List<CandidateTarget> candidateTargets;
        private boolean allowAbstain;
    }
}
