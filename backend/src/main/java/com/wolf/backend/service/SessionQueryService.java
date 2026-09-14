package com.wolf.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wolf.backend.dto.EventView;
import com.wolf.backend.dto.PendingActionView;
import com.wolf.backend.dto.PlayerView;
import com.wolf.backend.dto.SessionStateView;
import com.wolf.backend.engine.GameEngineService;
import com.wolf.backend.engine.RunningGame;
import com.wolf.backend.engine.RuntimePlayer;
import com.wolf.backend.entity.AiCharacter;
import com.wolf.backend.entity.AppUser;
import com.wolf.backend.entity.GameEventLog;
import com.wolf.backend.entity.GamePlayer;
import com.wolf.backend.entity.GameRoom;
import com.wolf.backend.entity.GameSession;
import com.wolf.backend.mapper.AiCharacterMapper;
import com.wolf.backend.mapper.AppUserMapper;
import com.wolf.backend.mapper.GameEventLogMapper;
import com.wolf.backend.mapper.GamePlayerMapper;
import com.wolf.backend.mapper.GameRoomMapper;
import com.wolf.backend.mapper.GameSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对应文档17节 GET /api/sessions/{sessionId}/state：
 * "按调用者权限过滤visible_scope，绝不返回超出该玩家可见范围的信息"。
 */
@Service
@RequiredArgsConstructor
public class SessionQueryService {

    // 系统旁白类事件（借用一个真实玩家id满足NOT NULL约束）不应展示成该玩家说的话
    private static final java.util.Set<String> SYSTEM_EVENT_TYPES = java.util.Set.of(
            "death_announcement", "vote_result", "settlement", "game_paused", "game_resumed", "night_announcement");

    private final GameSessionMapper sessionMapper;
    private final GameRoomMapper roomMapper;
    private final GamePlayerMapper playerMapper;
    private final GameEventLogMapper eventLogMapper;
    private final AiCharacterMapper characterMapper;
    private final AppUserMapper appUserMapper;
    private final GameEngineService gameEngineService;

    private static final int RECENT_EVENTS_LIMIT = 200;

    public SessionStateView getState(Long sessionId, Long requestPlayerId, boolean godViewRequested) {
        GameSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("对局不存在: " + sessionId);
        }
        RunningGame game = gameEngineService.getRunningGame(sessionId);
        boolean settled = "settled".equals(session.getStatus());
        GameRoom room = roomMapper.selectById(session.getRoomId());
        // 人机混战局room.show_ai_inner_monologue强制为0，见文档8.4节，此处双重把关godView不被滥用
        boolean godView = godViewRequested && room != null && room.getShowAiInnerMonologue() != null
                && room.getShowAiInnerMonologue() == 1;

        List<GamePlayer> players = playerMapper.selectList(
                new LambdaQueryWrapper<GamePlayer>().eq(GamePlayer::getSessionId, sessionId));
        List<Long> characterIds = players.stream().map(GamePlayer::getCharacterId).filter(java.util.Objects::nonNull).toList();
        List<Long> userIds = players.stream().map(GamePlayer::getUserId).filter(java.util.Objects::nonNull).toList();
        Map<Long, AiCharacter> characterCache = characterIds.isEmpty() ? Map.of() : characterMapper.selectBatchIds(characterIds)
                .stream().collect(Collectors.toMap(AiCharacter::getId, c -> c));
        Map<Long, AppUser> userCache = userIds.isEmpty() ? Map.of() : appUserMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(AppUser::getId, u -> u));

        String requesterCamp = null;
        if (requestPlayerId != null) {
            GamePlayer self = players.stream().filter(p -> p.getId().equals(requestPlayerId)).findFirst().orElse(null);
            if (self != null) requesterCamp = self.getCamp();
        }
        final boolean requesterIsWerewolf = "werewolf".equals(requesterCamp);
        boolean canSeeAllRoles = settled || godView;
        // 警长竞选结果在对局进行中只存在于内存态RunningGame里，session.sheriff_player_id要到settleGame()才落库，
        // 这里必须优先读活跃对局的实时值，否则竞选结束后前端永远看不到警长标记
        final Long effectiveSheriffPlayerId = game != null ? game.getSheriffPlayerId() : session.getSheriffPlayerId();

        List<PlayerView> playerViews = players.stream().sorted((a, b) -> a.getSeatNo() - b.getSeatNo()).map(p -> {
            PlayerView v = new PlayerView();
            v.setPlayerId(p.getId());
            v.setSeatNo(p.getSeatNo());
            v.setAi(p.getIsAi() == 1);
            v.setAlive(p.getIsAlive() == 1);
            v.setEliminatedDay(p.getEliminatedDay());
            v.setEliminatedReason(p.getEliminatedReason());
            if (p.getIsAi() == 1) {
                AiCharacter c = characterCache.get(p.getCharacterId());
                v.setDisplayName(c != null ? c.getName() : ("AI" + p.getCharacterId()));
                v.setAvatarUrl(c != null ? c.getAvatarUrl() : null);
            } else {
                AppUser u = userCache.get(p.getUserId());
                v.setDisplayName(u != null ? u.getNickname() : ("玩家" + p.getUserId()));
            }
            boolean isSelf = requestPlayerId != null && requestPlayerId.equals(p.getId());
            boolean wolfPeerVisible = requesterIsWerewolf && "werewolf".equals(p.getCamp());
            // 白痴被投票出局翻牌免死后，身份对所有人永久公开，见新增规则
            boolean publiclyRevealed = p.getIsRevealed() != null && p.getIsRevealed() == 1;
            if (canSeeAllRoles || isSelf || wolfPeerVisible || publiclyRevealed) {
                v.setInGameRole(p.getInGameRole());
                v.setCamp(p.getCamp());
            }
            if (effectiveSheriffPlayerId != null) {
                v.setSheriff(effectiveSheriffPlayerId.equals(p.getId()));
            }
            return v;
        }).toList();

        List<GameEventLog> logs = eventLogMapper.selectList(
                new LambdaQueryWrapper<GameEventLog>()
                        .eq(GameEventLog::getSessionId, sessionId) // 严格限定session，见文档6.4节
                        .orderByDesc(GameEventLog::getId)
                        .last("limit " + RECENT_EVENTS_LIMIT));
        Map<Long, PlayerView> playerViewById = playerViews.stream().collect(Collectors.toMap(PlayerView::getPlayerId, v -> v));

        List<EventView> eventViews = logs.stream()
                .filter(log -> visible(log, requestPlayerId, requesterIsWerewolf, godView))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(log -> {
                    EventView v = new EventView();
                    v.setId(log.getId());
                    v.setDay(log.getDay());
                    v.setPhase(log.getPhase());
                    v.setActorPlayerId(log.getActorPlayerId());
                    if (SYSTEM_EVENT_TYPES.contains(log.getEventType())) {
                        v.setActorName("系统");
                    } else {
                        PlayerView actor = playerViewById.get(log.getActorPlayerId());
                        v.setActorName(actor != null ? actor.getDisplayName() : null);
                    }
                    v.setEventType(log.getEventType());
                    v.setTargetPlayerId(log.getTargetPlayerId());
                    if (log.getTargetPlayerId() != null) {
                        PlayerView target = playerViewById.get(log.getTargetPlayerId());
                        v.setTargetName(target != null ? target.getDisplayName() : null);
                    }
                    v.setClaimedRole(log.getClaimedRole());
                    v.setContent(log.getContent());
                    v.setVisibleScope(log.getVisibleScope());
                    v.setCreatedAt(log.getCreatedAt());
                    return v;
                }).toList();

        SessionStateView view = new SessionStateView();
        view.setSessionId(session.getId());
        view.setRoomId(session.getRoomId());
        view.setDayCount(game != null ? game.getDayCount() : (session.getDayCount() != null ? session.getDayCount() : 0));
        view.setPhase(game != null ? game.getPhase() : session.getPhase());
        view.setStatus(session.getStatus());
        view.setWinnerCamp(session.getWinnerCamp());
        view.setSheriffPlayerId(effectiveSheriffPlayerId);
        view.setPaused(game != null && game.isPaused());
        view.setPlayers(playerViews);
        view.setRecentEvents(eventViews);
        view.setMyPlayerId(requestPlayerId);
        if (requestPlayerId != null) {
            PlayerView self = playerViewById.get(requestPlayerId);
            view.setMyInGameRole(self != null ? self.getInGameRole() : null);
        }
        if (game != null && requestPlayerId != null) {
            PendingActionView pending = game.getPendingViews().get(requestPlayerId);
            view.setPendingAction(pending);
        }
        return view;
    }

    /**
     * 对应文档17节 GET /api/sessions/{sessionId}/replay：仅支持已结算对局，
     * 全量事件+真实身份不做visible_scope过滤（结算后亮牌），供回放/视频生成使用。
     */
    public com.wolf.backend.dto.ReplayView getReplay(Long sessionId, boolean includeInnerMonologue) {
        GameSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("对局不存在: " + sessionId);
        }
        if (!"settled".equals(session.getStatus())) {
            throw new IllegalStateException("对局尚未结束，暂不支持导出回放: " + sessionId);
        }

        List<GamePlayer> players = playerMapper.selectList(
                new LambdaQueryWrapper<GamePlayer>().eq(GamePlayer::getSessionId, sessionId));
        List<Long> characterIds = players.stream().map(GamePlayer::getCharacterId).filter(java.util.Objects::nonNull).toList();
        List<Long> userIds = players.stream().map(GamePlayer::getUserId).filter(java.util.Objects::nonNull).toList();
        Map<Long, AiCharacter> characterCache = characterIds.isEmpty() ? Map.of() : characterMapper.selectBatchIds(characterIds)
                .stream().collect(Collectors.toMap(AiCharacter::getId, c -> c));
        Map<Long, AppUser> userCache = userIds.isEmpty() ? Map.of() : appUserMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(AppUser::getId, u -> u));

        List<PlayerView> playerViews = players.stream().sorted((a, b) -> a.getSeatNo() - b.getSeatNo()).map(p -> {
            PlayerView v = new PlayerView();
            v.setPlayerId(p.getId());
            v.setSeatNo(p.getSeatNo());
            v.setAi(p.getIsAi() == 1);
            v.setAlive(p.getIsAlive() == 1);
            v.setEliminatedDay(p.getEliminatedDay());
            v.setEliminatedReason(p.getEliminatedReason());
            if (p.getIsAi() == 1) {
                AiCharacter c = characterCache.get(p.getCharacterId());
                v.setDisplayName(c != null ? c.getName() : ("AI" + p.getCharacterId()));
                v.setAvatarUrl(c != null ? c.getAvatarUrl() : null);
            } else {
                AppUser u = userCache.get(p.getUserId());
                v.setDisplayName(u != null ? u.getNickname() : ("玩家" + p.getUserId()));
            }
            // 结算后一律亮牌，见文档3.3节SETTLING阶段说明
            v.setInGameRole(p.getInGameRole());
            v.setCamp(p.getCamp());
            if (session.getSheriffPlayerId() != null) {
                v.setSheriff(session.getSheriffPlayerId().equals(p.getId()));
            }
            return v;
        }).toList();
        Map<Long, PlayerView> playerViewById = playerViews.stream().collect(Collectors.toMap(PlayerView::getPlayerId, v -> v));

        List<GameEventLog> logs = eventLogMapper.selectList(
                new LambdaQueryWrapper<GameEventLog>()
                        .eq(GameEventLog::getSessionId, sessionId) // 严格限定session，见文档6.4节
                        .orderByAsc(GameEventLog::getId));

        List<EventView> eventViews = logs.stream()
                .filter(log -> includeInnerMonologue || !"god_view_only".equals(log.getVisibleScope()))
                .map(log -> {
                    EventView v = new EventView();
                    v.setId(log.getId());
                    v.setDay(log.getDay());
                    v.setPhase(log.getPhase());
                    v.setActorPlayerId(log.getActorPlayerId());
                    if (SYSTEM_EVENT_TYPES.contains(log.getEventType())) {
                        v.setActorName("系统");
                    } else {
                        PlayerView actor = playerViewById.get(log.getActorPlayerId());
                        v.setActorName(actor != null ? actor.getDisplayName() : null);
                    }
                    v.setEventType(log.getEventType());
                    v.setTargetPlayerId(log.getTargetPlayerId());
                    if (log.getTargetPlayerId() != null) {
                        PlayerView target = playerViewById.get(log.getTargetPlayerId());
                        v.setTargetName(target != null ? target.getDisplayName() : null);
                    }
                    v.setClaimedRole(log.getClaimedRole());
                    v.setContent(log.getContent());
                    v.setVisibleScope(log.getVisibleScope());
                    v.setCreatedAt(log.getCreatedAt());
                    return v;
                }).toList();

        com.wolf.backend.dto.ReplayView replay = new com.wolf.backend.dto.ReplayView();
        replay.setSessionId(session.getId());
        replay.setRoomId(session.getRoomId());
        replay.setDayCount(session.getDayCount() != null ? session.getDayCount() : 0);
        replay.setWinnerCamp(session.getWinnerCamp());
        replay.setPlayers(playerViews);
        replay.setEvents(eventViews);
        return replay;
    }

    private boolean visible(GameEventLog log, Long requestPlayerId, boolean requesterIsWerewolf, boolean godView) {
        String scope = log.getVisibleScope();
        if ("public".equals(scope)) return true;
        if ("god_view_only".equals(scope)) return godView;
        if ("werewolf_team".equals(scope)) return requesterIsWerewolf || godView;
        if ("private_to_actor".equals(scope)) return godView || (requestPlayerId != null && requestPlayerId.equals(log.getActorPlayerId()));
        return false;
    }
}
