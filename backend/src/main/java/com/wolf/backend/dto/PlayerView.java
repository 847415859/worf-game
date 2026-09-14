package com.wolf.backend.dto;

import lombok.Data;

@Data
public class PlayerView {
    private Long playerId;
    private int seatNo;
    private boolean ai;
    private boolean alive;
    private String displayName;
    private String avatarUrl;
    /** 仅当请求者有权限看到（自己/上帝视角/结算后）时才非空，见文档8.4节 */
    private String inGameRole;
    private String camp;
    private Integer eliminatedDay;
    private String eliminatedReason;
    private boolean isSheriff;
}
