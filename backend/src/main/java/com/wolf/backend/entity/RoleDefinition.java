package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色定义表（对应文档3.4/7.6节）：狼人杀的每个身份是什么、属于哪个阵营、有什么技能——
 * 全部定义成数据而不是写死在代码里，新增一个角色（如骑士/禁言长老）原则上只需插入一条
 * 记录+配置skill_config JSON，不需要改对局引擎代码。
 */
@Data
@TableName("role_definition")
public class RoleDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 角色唯一标识，被{@link GamePlayer#getInGameRole()}/room_template.role_composition/
     * game_event_log.claimed_role引用。当前已实现：werewolf狼人/seer预言家/witch女巫/
     * hunter猎人/villager平民/idiot白痴
     */
    private String roleKey;
    /** 角色中文展示名，如"预言家" */
    private String roleName;
    /** 所属阵营：werewolf=狼人阵营；good=好人阵营（含神职与平民）。third_party第三方阵营预留但当前未启用任何角色 */
    private String camp;
    /**
     * 是否为"神职"（好人阵营里拥有主动技能的角色，如预言家/女巫/猎人/白痴），1=是，0=否（平民）。
     * 屠边胜负判定用它来区分"神职全灭"和"平民全灭"两种狼人获胜条件，见GameEngineService#checkWinCondition
     */
    private Integer isGodRole;
    /**
     * 技能配置，数据驱动对局引擎的技能结算逻辑，不需要为新角色改代码。常规夜间技能形如
     * {@code {"night_action":{"order":显示行动顺序,"action_type":"kill|check_identity|use_potion",
     * "frequency":"every_night|limited","result_visible_scope":"self|werewolf_team",...}}}；
     * 死亡触发型技能形如 {@code {"trigger_action":{"trigger_event":"on_death",
     * "condition":"death_cause != poison","action_type":"shoot"}}}；
     * 无主动技能角色(如平民/白痴)此字段为NULL
     */
    @JsonRawValue
    private String skillConfig;
    /** 技能一句话说明，供管理后台/前端展示 */
    private String description;
    /** 1=当前版本启用；0=未启用/灰度中，用于新角色逐步上线 */
    private Integer status;
    private LocalDateTime createdAt;
}
