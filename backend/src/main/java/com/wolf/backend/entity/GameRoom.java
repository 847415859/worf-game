package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间表（对应文档3.2/7.8节）：记录一次"开局配置"——角色板、席位安排、规则参数的组合，
 * 是{@link GameSession}的上一层容器；一个房间理论上可以对应多个session（重开一局），
 * 但当前前端实现是房间与对局一一对应的简化用法。
 */
@Data
@TableName("game_room")
public class GameRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 房间名称，房主自定义，纯展示用途 */
    private String roomName;
    /**
     * 开局模式：human_ai_mixed=人机混战；all_ai=全AI观赏局；custom=自定义席位与人数比例。
     * 三种模式在数据结构上不做区分，本质是seatConfig取值不同的同一套房间配置，见文档3.1节
     */
    private String mode;
    /** 引用{@link RoomTemplate#getId()}；为空表示未使用模板、完全自定义roleBoard */
    private Long templateId;
    /** 本局最终生效的角色搭配（复制自模板或手动指定），结构同RoomTemplate.roleComposition */
    @JsonRawValue
    private String roleBoard;
    /**
     * 开局前的席位预分配快照，JSON数组，每项形如 {@code {"seat_no":1,"occupant_type":"human|ai",
     * "user_id":null,"character_id":null,"auto_match_tier":"bronze"}}；human席位的user_id在
     * 玩家/api/rooms/{id}/join后回填，ai席位的character_id可预先指定或留空走auto_match_tier
     * 自动匹配，见文档3.2节
     */
    @JsonRawValue
    private String seatConfig;
    /**
     * 本局生效的规则基准配置快照，JSON对象，字段含义见{@link com.wolf.backend.dto.RuleConfig}
     * （警长票权/女巫用药限制/遗言策略/平票处理/非理性系数/超时兜底等，对应需求文档3.2/14.2节的
     * 规则版本声明），开局时从RuleConfig默认值出发、按需覆盖后固化存储，同一局中途不再改变
     */
    @JsonRawValue
    private String ruleConfig;
    /** 1=允许非参与者围观本局；0=仅参与者可见（当前前端未做强制校验，字段已预留） */
    private Integer spectatorPublic;
    /**
     * 观众是否可见AI的内心OS（内部推理链）。人机混战局(存在human席位)建房时会被
     * RoomService强制置0，防止真人玩家或观众看到AI推理等于开天眼作弊，见文档8.4节
     */
    private Integer showAiInnerMonologue;
    /**
     * 房间生命周期状态：created=已建房未开局(理论初始值，RoomService建房后立即置为
     * seat_filling)；seat_filling=等待真人玩家加入席位；in_progress=已开始、对局进行中；
     * settled=对局已结算结束
     */
    private String status;
    /** 房主的app_user.id */
    private Long createdBy;
    private LocalDateTime createdAt;
}
