package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 极简用户表：MVP不做完整账号体系（无密码/无登录态），仅在真人玩家加入房间时按昵称登记一条记录，
 * 满足{@link GamePlayer#getUserId()}外键需要，未来接入正式账号体系时可平滑替换。
 */
@Data
@TableName("app_user")
public class AppUser {
    /** 用户ID，被game_player.user_id引用 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 玩家昵称，加入房间时填写，无需注册/登录 */
    private String nickname;
    /** 首次加入房间时创建的时间 */
    private LocalDateTime createdAt;
}
