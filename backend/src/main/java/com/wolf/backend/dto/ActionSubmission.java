package com.wolf.backend.dto;

import lombok.Data;

/** 对应需求文档17节 /api/sessions/{sessionId}/actions 请求体 */
@Data
public class ActionSubmission {
    /** 提交者的 game_player.id，由加入房间/拉取状态接口返回给客户端 */
    private Long playerId;

    /**
     * speech / vote / night_action / last_words / sheriff_campaign_speech /
     * sheriff_vote / pk_speech / badge_transfer
     */
    private String actionType;

    private String content;
    private Long targetPlayerId;

    /** 是否/参选/使用技能类布尔决策（sheriff_campaign_decision、女巫用药意愿等） */
    private Boolean decisionYes;

    /** 悍跳等战术使用：本条发言的自称身份，见文档5.4节 claimed_role */
    private String claimedRole;

    /** badge_transfer 专用：true表示毁徽而不是移交 */
    private boolean destroyBadge;
}
