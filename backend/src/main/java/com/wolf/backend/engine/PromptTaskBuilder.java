package com.wolf.backend.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wolf.backend.ai.AiDecideRequest;
import com.wolf.backend.entity.GameEventLog;
import com.wolf.backend.mapper.GameEventLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装文档8.1节 System Prompt 的"当前对局状态/本局事件窗口/本回合任务"部分，
 * 转成AI编排服务的请求契约。MVP阶段不接入4.3节长期记忆与4.2节关系上下文（见路线图V1）。
 */
@Component
@RequiredArgsConstructor
public class PromptTaskBuilder {

    private static final int EVENT_WINDOW_LIMIT = 60;

    private final GameEventLogMapper eventLogMapper;

    public AiDecideRequest build(RunningGame game, RuntimePlayer self, TaskSpec spec) {
        AiDecideRequest.CharacterInfo character = new AiDecideRequest.CharacterInfo(
                self.getCharacterId(), self.getDisplayName(), self.getBackstory(),
                self.getPersonalityVector(), self.getSpeechStyle());

        AiDecideRequest.SelfInfo selfInfo = new AiDecideRequest.SelfInfo(
                self.getPlayerId(), self.getSeatNo(), self.getInGameRole(), self.getCamp(),
                self.isSheriff(), self.isAlive());

        List<AiDecideRequest.PlayerInfo> players = new ArrayList<>();
        boolean viewerIsWerewolf = "werewolf".equals(self.getCamp());
        for (RuntimePlayer p : game.getPlayers()) {
            String knownCamp = null;
            if (viewerIsWerewolf && "werewolf".equals(p.getCamp())) {
                knownCamp = "werewolf"; // 狼人互相认识队友，见文档8.4节
            }
            players.add(new AiDecideRequest.PlayerInfo(p.getPlayerId(), p.getSeatNo(), p.getDisplayName(),
                    p.isAlive(), p.isSheriff(), knownCamp));
        }

        List<AiDecideRequest.EventWindowItem> window = buildEventWindow(game, self);

        AiDecideRequest.GameContext context = new AiDecideRequest.GameContext(
                game.getSessionId(), game.getDayCount(), game.getPhase(), selfInfo, players, window);

        List<AiDecideRequest.CandidateTarget> candidates = new ArrayList<>();
        if (spec.getCandidateTargets() != null) {
            for (RuntimePlayer c : spec.getCandidateTargets()) {
                candidates.add(new AiDecideRequest.CandidateTarget(c.getPlayerId(), c.getDisplayName()));
            }
        }
        AiDecideRequest.Task task = new AiDecideRequest.Task(spec.getActionType(), spec.getInstruction(), candidates, spec.isAllowAbstain());

        return new AiDecideRequest(character, context, task);
    }

    private List<AiDecideRequest.EventWindowItem> buildEventWindow(RunningGame game, RuntimePlayer self) {
        LambdaQueryWrapper<GameEventLog> query = new LambdaQueryWrapper<GameEventLog>()
                .eq(GameEventLog::getSessionId, game.getSessionId()) // 严格限定session，见文档6.4节
                .orderByDesc(GameEventLog::getId)
                .last("limit " + EVENT_WINDOW_LIMIT);
        List<GameEventLog> logs = eventLogMapper.selectList(query);

        List<AiDecideRequest.EventWindowItem> items = new ArrayList<>();
        boolean isWerewolf = "werewolf".equals(self.getCamp());
        for (int i = logs.size() - 1; i >= 0; i--) {
            GameEventLog log = logs.get(i);
            if (!visibleTo(log, self, isWerewolf)) {
                continue;
            }
            RuntimePlayer actor = game.byPlayerId(log.getActorPlayerId());
            RuntimePlayer target = log.getTargetPlayerId() != null ? game.byPlayerId(log.getTargetPlayerId()) : null;
            items.add(new AiDecideRequest.EventWindowItem(log.getDay(), log.getPhase(),
                    actor != null ? actor.getDisplayName() : "未知",
                    log.getEventType(), log.getClaimedRole(),
                    target != null ? target.getDisplayName() : null,
                    log.getContent()));
        }
        return items;
    }

    private boolean visibleTo(GameEventLog log, RuntimePlayer self, boolean isWerewolf) {
        String scope = log.getVisibleScope();
        if ("public".equals(scope)) {
            return true;
        }
        if ("werewolf_team".equals(scope)) {
            return isWerewolf;
        }
        if ("private_to_actor".equals(scope)) {
            return self.getPlayerId().equals(log.getActorPlayerId());
        }
        // god_view_only（内心OS等）永不进入其他角色的Prompt，见文档8.4节
        return false;
    }
}
