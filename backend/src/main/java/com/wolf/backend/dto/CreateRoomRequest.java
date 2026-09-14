package com.wolf.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoomRequest {
    private String roomName;

    /** human_ai_mixed / all_ai / custom，见文档3.1节 */
    @NotNull
    private String mode;

    private Long templateId;

    @NotEmpty
    private List<RoleBoardItem> roleBoard;

    @NotEmpty
    private List<SeatConfigItem> seats;

    private boolean spectatorPublic = true;
    private boolean showAiInnerMonologue = false;

    /** 未提供的字段使用 RuleConfig 默认值 */
    private RuleConfig ruleConfig;

    private Long createdBy;
}
