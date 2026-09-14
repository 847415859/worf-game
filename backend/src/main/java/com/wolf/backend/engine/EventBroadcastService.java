package com.wolf.backend.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wolf.backend.dto.EventView;
import com.wolf.backend.entity.GameEventLog;
import com.wolf.backend.mapper.GameEventLogMapper;
import com.wolf.backend.ws.ClientConnection;
import com.wolf.backend.ws.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 持久化对局事件并按 visible_scope 过滤推送，见文档8.4节防作弊与信息隔离、
 * 6.4节"事件窗口查询必须限定session"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBroadcastService {

    private final GameEventLogMapper eventLogMapper;
    private final GameWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;

    public GameEventLog record(RunningGame game, Long actorPlayerId, String eventType, Long targetPlayerId,
                                String claimedRole, String content, String visibleScope, boolean fallback) {
        GameEventLog log = new GameEventLog();
        log.setSessionId(game.getSessionId());
        log.setDay(game.getDayCount());
        log.setPhase(game.getPhase());
        log.setActorPlayerId(actorPlayerId);
        log.setEventType(eventType);
        log.setTargetPlayerId(targetPlayerId);
        log.setClaimedRole(claimedRole);
        log.setContent(content);
        log.setVisibleScope(visibleScope);
        log.setIsFallback(fallback ? 1 : 0);
        eventLogMapper.insert(log);
        broadcast(game, log);
        return log;
    }

    public void broadcastPhaseChange(RunningGame game) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "phase_change");
        payload.put("sessionId", game.getSessionId());
        payload.put("day", game.getDayCount());
        payload.put("phase", game.getPhase());
        String json = toJson(payload);
        for (ClientConnection conn : wsHandler.connectionsFor(game.getSessionId())) {
            wsHandler.sendTo(conn, json);
        }
    }

    private void broadcast(RunningGame game, GameEventLog log) {
        EventView view = toView(game, log);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event");
        payload.put("event", view);
        String json = toJson(payload);

        for (ClientConnection conn : wsHandler.connectionsFor(game.getSessionId())) {
            if (isVisible(game, log, conn)) {
                wsHandler.sendTo(conn, json);
            }
        }
    }

    private boolean isVisible(RunningGame game, GameEventLog log, ClientConnection conn) {
        String scope = log.getVisibleScope();
        if ("public".equals(scope)) {
            return true;
        }
        if ("god_view_only".equals(scope)) {
            return game.isShowAiInnerMonologue() && conn.isGodView();
        }
        if ("werewolf_team".equals(scope)) {
            if (game.isShowAiInnerMonologue() && conn.isGodView()) {
                return true;
            }
            RuntimePlayer viewer = conn.getPlayerId() != null ? game.byPlayerId(conn.getPlayerId()) : null;
            return viewer != null && "werewolf".equals(viewer.getCamp());
        }
        if ("private_to_actor".equals(scope)) {
            if (game.isShowAiInnerMonologue() && conn.isGodView()) {
                return true;
            }
            return conn.getPlayerId() != null && conn.getPlayerId().equals(log.getActorPlayerId());
        }
        return false;
    }

    // 系统旁白类事件借用一个真实玩家id满足NOT NULL约束（见GameEngineService#systemActorId），
    // 展示时不应把这类公告误标注成该玩家说的话
    private static final java.util.Set<String> SYSTEM_EVENT_TYPES = java.util.Set.of(
            "death_announcement", "vote_result", "settlement", "game_paused", "game_resumed", "night_announcement");

    private EventView toView(RunningGame game, GameEventLog log) {
        EventView view = new EventView();
        view.setId(log.getId());
        view.setDay(log.getDay());
        view.setPhase(log.getPhase());
        view.setActorPlayerId(log.getActorPlayerId());
        if (SYSTEM_EVENT_TYPES.contains(log.getEventType())) {
            view.setActorName("系统");
        } else {
            RuntimePlayer actor = game.byPlayerId(log.getActorPlayerId());
            view.setActorName(actor != null ? actor.getDisplayName() : null);
        }
        view.setEventType(log.getEventType());
        view.setTargetPlayerId(log.getTargetPlayerId());
        if (log.getTargetPlayerId() != null) {
            RuntimePlayer target = game.byPlayerId(log.getTargetPlayerId());
            view.setTargetName(target != null ? target.getDisplayName() : null);
        }
        view.setClaimedRole(log.getClaimedRole());
        view.setContent(log.getContent());
        view.setVisibleScope(log.getVisibleScope());
        view.setCreatedAt(log.getCreatedAt());
        return view;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("序列化WS消息失败", e);
            return "{}";
        }
    }
}
