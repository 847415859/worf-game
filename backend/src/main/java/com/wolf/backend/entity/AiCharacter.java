package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI角色人格主表（对应文档4.1/7.1节）。
 * 每一行是一个拥有独立人设、跨对局持久存在的"数字演员"，与某一局游戏里临时分配到的
 * 对局身份（werewolf/seer等，见{@link GamePlayer#getInGameRole()}）是两回事——
 * 同一个AI角色这局可能是狼人，下局可能是女巫，见文档第2章术语定义。
 */
@Data
@TableName("ai_character")
public class AiCharacter {
    /** AI角色ID，跨对局持久不变，是"演员"本身而非某一局的临时身份 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色显示名，如"周明远" */
    private String name;
    /** 头像图片URL，MVP阶段可为空（前端用文字头像占位） */
    private String avatarUrl;
    /** 人物小传，自然语言描述性格/说话习惯/与其他角色的恩怨，会直接拼进AI的System Prompt */
    private String backstory;
    /**
     * 结构化人格向量，JSON对象，8个0-100的整数字段：extroversion外向性/aggression攻击性/
     * logic_rigor逻辑严谨度/deception欺骗倾向/conformity从众性/emotional_stability情绪稳定性/
     * grudge记仇强度/humor幽默感，见文档4.1节
     */
    @JsonRawValue
    private String personalityVector;
    /**
     * 说话风格参数，JSON对象：catchphrases(口头禅字符串数组)/sentence_length(short|medium|long)/
     * emoji_usage(low|medium|high)，用于让不同角色"一听就能听出来是谁"
     */
    @JsonRawValue
    private String speechStyle;
    /** 综合评分缓存字段，MVP阶段未接入真正的成长复盘系统，暂为固定初始值，不参与游戏逻辑判断 */
    private Integer overallScore;
    /**
     * 段位（当前仅使用bronze，成长体系V2上线后启用silver/gold/diamond/king等分层），
     * 对应3.2节房间配置里的auto_match_tier自动匹配依据
     */
    private String tierKey;
    /** 1=启用，可被匹配入座；0=封禁/下线，不再进入新对局但历史数据保留 */
    private Integer status;
    /** 角色来源：system=内置种子角色；user_created=玩家自建角色（UGC，V3路线图规划，MVP阶段未开放前端入口） */
    private String source;
    /** 若source=user_created，记录创建者的app_user.id；系统内置角色为空 */
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
