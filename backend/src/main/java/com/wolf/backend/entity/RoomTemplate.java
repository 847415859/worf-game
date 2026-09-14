package com.wolf.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准局模板表（对应文档3.4/3.5/7.7节）：把"多少人配多少神职多少狼"这种搭配预先存成
 * 可复用的数据，开局时一键套用，也支持完全不选模板、手动自定义role_board。
 */
@Data
@TableName("room_template")
public class RoomTemplate {
    /** 模板ID，被{@link GameRoom#getTemplateId()}引用 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模板展示名，如"经典9人局""经典12人局" */
    private String templateName;
    /** 本模板固定的总席位数，必须等于roleComposition里所有count之和 */
    private Integer totalSeats;
    /**
     * 角色搭配，JSON数组，每项形如 {@code {"role_key":"werewolf","count":3}}，
     * role_key引用{@link RoleDefinition#getRoleKey()}；开局时按此展开成角色列表再随机分配座位
     */
    @JsonRawValue
    private String roleComposition;
    /** system=内置标准局模板；custom=用户自建并保存的模板（后端已支持存储，前端尚未开放"保存为模板"入口） */
    private String templateType;
    /** 若templateType=custom，记录创建者的app_user.id */
    private Long createdBy;
    private LocalDateTime createdAt;
}
