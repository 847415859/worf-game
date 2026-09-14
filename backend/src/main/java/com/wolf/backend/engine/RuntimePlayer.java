package com.wolf.backend.engine;

import lombok.Data;

/** 一局游戏运行期内的席位状态（内存态，落库由GameEngineService显式触发） */
@Data
public class RuntimePlayer {
    private Long playerId;      // game_player.id
    private int seatNo;
    private boolean ai;
    private Long characterId;
    private Long userId;
    private String displayName;
    private String avatarUrl;

    private String inGameRole;  // werewolf/seer/witch/hunter/villager/idiot
    private String camp;        // werewolf/good
    private volatile boolean alive = true;
    private Integer eliminatedDay;
    private String eliminatedReason; // wolf_kill/poison/vote_out/hunter_shot/self_destruct
    private volatile boolean sheriff = false;
    private String lastClaimedRole;

    // 白痴翻牌免死后：永久失去投票权，身份对所有人公开可见
    private volatile boolean canVote = true;
    private volatile boolean revealed = false;

    // AI人格快照，开局时从ai_character一次性加载，避免每回合重复查库
    private String backstory;
    private Object personalityVector;
    private Object speechStyle;

    // 女巫专属技能剩余次数（其余角色不使用）
    private boolean witchHealAvailable = true;
    private boolean witchPoisonAvailable = true;

    // 猎人是否已经触发过技能（防重复触发）
    private boolean skillTriggered = false;
}
