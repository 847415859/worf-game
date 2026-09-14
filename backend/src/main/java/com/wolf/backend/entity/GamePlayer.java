package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 对局内玩家席位表（对应文档7.10节）：把人类玩家和AI角色用同一套结构统一建模成"席位"，
 * 屏蔽掉背后是人还是AI的差异，游戏引擎/事件日志一律只认这张表的id（即前端/接口里的playerId）。
 */
@Data
@TableName("game_player")
public class GamePlayer {
    /**
     * 席位ID，即前端/接口里的playerId，是对局内引用一个参与者的唯一方式
     * （不直接用characterId或userId，因为同一角色/用户不同局的id含义不同）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属对局，引用{@link GameSession#getId()}；几乎所有查询都必须带上此过滤条件，
     * 绝不能只按characterId/userId查，否则会跨局窜话，见文档6.4节上下文隔离要求
     */
    private Long sessionId;
    /** 座位号(1..N)，同一session内唯一，决定发言顺序、警长左右手位等 */
    private Integer seatNo;
    /** 1=AI席位（characterId有效）；0=真人席位（userId有效） */
    private Integer isAi;
    /** isAi=1时有效，引用{@link AiCharacter#getId()}，记录本局由哪个AI角色出演该席位 */
    private Long characterId;
    /** isAi=0时有效，引用{@link AppUser#getId()} */
    private Long userId;
    /**
     * 本局真实身份(狼人杀角色)，引用{@link RoleDefinition#getRoleKey()}，开局时随机分配，
     * 游戏过程中除白痴翻牌等特殊规则外不公开给其他玩家
     */
    private String inGameRole;
    /** 真实阵营，冗余自RoleDefinition.camp，避免每次判定胜负都要JOIN查角色定义表 */
    private String camp;
    /** 1=存活；0=已出局（含各种死因，具体看eliminatedReason） */
    private Integer isAlive;
    /** 出局发生的天数(day_count)，配合eliminatedReason用于判断遗言/警徽移交/猎人开枪等后续流程是否触发 */
    private Integer eliminatedDay;
    /**
     * 出局原因：wolf_kill=夜间被狼人刀杀；poison=被女巫毒杀；vote_out=白天被投票放逐；
     * hunter_shot=被猎人开枪带走；self_destruct=狼人白天自爆。不同死因对应不同的遗言/
     * 技能触发规则，见文档3.8/3.9.2节
     */
    private String eliminatedReason;
    /**
     * 身份是否已被强制公开（目前唯一场景：白痴被投票出局后翻牌免死，永久失去投票权但身份对
     * 所有人公开），1=已公开，查询时据此覆盖常规的身份可见性规则
     */
    private Integer isRevealed;
}
