package com.wolf.backend.dto;

import lombok.Data;

@Data
public class JoinRoomRequest {
    /** 为空则视为新用户，用nickname创建一条app_user记录 */
    private Long userId;
    private String nickname;
    /** 不指定则自动分配第一个空闲的human席位 */
    private Integer seatNo;
}
