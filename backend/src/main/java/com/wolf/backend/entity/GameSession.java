package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对局会话表（对应文档7.9节）：一次真正跑起来的游戏进程。对局运行期间大量状态
 * (dayCount/phase/sheriffPlayerId等)以内存态{@link com.wolf.backend.engine.RunningGame}为准、
 * 只在结算时统一落库，是为了避免每个回合都写DB——查询这些字段时优先读内存态，
 * 见{@link com.wolf.backend.service.SessionQueryService}的实现。
 */
@Data
@TableName("game_session")
public class GameSession {
    /** 对局ID，即前端URL /sessions/{id} 与WebSocket /ws/sessions/{id} 中的sessionId */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属房间，引用{@link GameRoom#getId()} */
    private Long roomId;
    /** 当前天数快照，持久化时机见类注释 */
    private Integer dayCount;
    /**
     * 当前阶段快照，持久化时机见类注释。取值：role_assigning角色分配/sheriff_election警长竞选/
     * night夜晚/day_announce公布死讯/day_discuss白天讨论/day_vote投票/day_pk_revote平票PK重投/
     * settling结算中/settled已结束
     */
    private String phase;
    /** 当前警长的game_player.id快照，为空表示未产生警长/警徽已销毁/未开启警长机制 */
    private Long sheriffPlayerId;
    /** 对局状态：running=进行中；settled=已结算（正常结束/胜负判定/房主强制结束三种情况都会落到这个状态） */
    private String status;
    /** 获胜阵营，仅settled状态有值：werewolf=狼人屠边获胜；good=好人放逐所有狼人获胜；aborted=被强制结束，未分出胜负 */
    private String winnerCamp;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
