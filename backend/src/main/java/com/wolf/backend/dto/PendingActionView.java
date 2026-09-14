package com.wolf.backend.dto;

import lombok.Data;

/** 告知调用者：当前是否轮到TA行动、需要提交什么类型的动作 */
@Data
public class PendingActionView {
    private String actionType;
    private String promptHint;
    private Integer timeoutSeconds;
    /** vote/night_action/pk_speech 等场景下，候选目标玩家id列表 */
    private java.util.List<Long> candidateTargetPlayerIds;
}
