package com.wolf.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 对应文档17节 GET /api/sessions/{sessionId}/replay：仅支持已结算的对局，
 * 全量事件+真实身份不做visible_scope过滤（结算后亮牌，视频/复盘生成专用）。
 */
@Data
public class ReplayView {
    private Long sessionId;
    private Long roomId;
    private int dayCount;
    private String winnerCamp;
    private List<PlayerView> players;
    private List<EventView> events;
}
