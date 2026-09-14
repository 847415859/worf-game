package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对局事件日志（对应文档7.11节）：全量记录一局游戏里发生的每一件事，是Prompt组装
 * "本局事件窗口"（见文档8.1节）的数据来源、前端对局记录展示的数据来源，也是对局
 * 视频生成服务(video-service)回放渲染的唯一数据来源。
 */
@Data
@TableName("game_event_log")
public class GameEventLog {
    /** 事件ID，前端按此升序展示时间线、视频生成服务按此顺序回放 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属对局，引用{@link GameSession#getId()}；查询"本局事件窗口"必须显式带上此过滤条件，见文档6.4节 */
    private Long sessionId;
    /** 事件发生时的天数(day_count)快照 */
    private Integer day;
    /** 事件发生时的阶段快照，取值同{@link GameSession#getPhase()} */
    private String phase;
    /**
     * 事件的"发出方"，引用{@link GamePlayer#getId()}。系统旁白类事件(death_announcement/
     * vote_result/settlement/game_paused/game_resumed/night_announcement)借用第一个玩家的id
     * 满足NOT NULL约束，前端/回放展示时会特殊处理成"系统"而不是该玩家说的话，
     * 见{@code EventBroadcastService.SYSTEM_EVENT_TYPES}
     */
    private Long actorPlayerId;
    /**
     * 事件类型，当前已实现：speech发言/vote投票/night_action夜间行动/last_words遗言/
     * sheriff_campaign_speech警长竞选发言/sheriff_vote警长竞选投票/sheriff_elected警长当选/
     * pk_speech平票PK发言/pk_revote平票PK重投/vote_result投票结果/death_announcement死亡公告/
     * badge_transfer警徽移交/badge_destroyed警徽销毁/skill_trigger技能触发(猎人开枪等)/
     * settlement最终结算公告/role_assigned开局身份分配(仅本人可见)/inner_monologue内心OS(仅
     * 上帝视角可见)/game_paused对局暂停/game_resumed对局恢复/night_announcement法官夜间流程
     * 播报/idiot_revealed白痴翻牌
     */
    private String eventType;
    /** 事件指向的目标玩家(如投票对象/技能目标/开枪目标)，引用GamePlayer.id；纯发言类事件无目标时为空 */
    private Long targetPlayerId;
    /**
     * 发言中的"自称身份"(悍跳/倒钩等战术用)，与actor真实身份({@link GamePlayer#getInGameRole()})
     * 分离存储，用于复盘"悍跳是否被识破"，见文档5.4节
     */
    private String claimedRole;
    /** 事件文本内容：发言原文/系统公告文案/技能结果描述等 */
    private String content;
    /**
     * 可见范围，决定哪些客户端连接能收到推送、哪些角色的Prompt事件窗口能读到这条：
     * public=所有人可见；werewolf_team=仅狼人阵营可见(如夜间刀人商议)；
     * private_to_actor=仅事件发出者本人可见(如预言家查验结果)；
     * god_view_only=仅开启内心OS的上帝视角可见(AI内部推理链)，见文档8.4节防作弊设计
     */
    private String visibleScope;
    /**
     * 是否为内容审核拦截或AI调用超时/失败后的安全兜底内容（而非AI/玩家的真实原始输出），
     * 1=是兜底，供事后分析AI稳定性，见文档8.7/8.8节
     */
    private Integer isFallback;
    /** 事件写入时间，同一session内可配合id排序作为时间线 */
    private LocalDateTime createdAt;
}
