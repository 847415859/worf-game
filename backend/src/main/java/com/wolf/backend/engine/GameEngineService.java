package com.wolf.backend.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wolf.backend.ai.AiDecideRequest;
import com.wolf.backend.ai.AiDecideResponse;
import com.wolf.backend.ai.AiDecisionClient;
import com.wolf.backend.dto.ActionSubmission;
import com.wolf.backend.dto.RoleBoardItem;
import com.wolf.backend.dto.RuleConfig;
import com.wolf.backend.dto.SeatConfigItem;
import com.wolf.backend.entity.AiCharacter;
import com.wolf.backend.entity.AppUser;
import com.wolf.backend.entity.GamePlayer;
import com.wolf.backend.entity.GameRoom;
import com.wolf.backend.entity.GameSession;
import com.wolf.backend.entity.RoleDefinition;
import com.wolf.backend.mapper.AiCharacterMapper;
import com.wolf.backend.mapper.AppUserMapper;
import com.wolf.backend.mapper.GamePlayerMapper;
import com.wolf.backend.mapper.GameRoomMapper;
import com.wolf.backend.mapper.GameSessionMapper;
import com.wolf.backend.mapper.RoleDefinitionMapper;
import com.wolf.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 对局引擎：驱动文档3.3节的状态机（角色分配 -> 警长竞选 -> 昼夜循环 -> 结算）。
 * 每局游戏在独立线程内以阻塞方式顺序推进，AI回合调用AI编排服务，人类回合等待
 * /api/sessions/{id}/actions 提交，见文档6.4节"AI Agent编排服务不做角色级别的服务端会话缓存"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEngineService {

    private final GameRegistry registry;
    private final RoomService roomService;
    private final GameRoomMapper roomMapper;
    private final GameSessionMapper sessionMapper;
    private final GamePlayerMapper playerMapper;
    private final AiCharacterMapper characterMapper;
    private final RoleDefinitionMapper roleDefinitionMapper;
    private final AppUserMapper appUserMapper;
    private final EventBroadcastService eventBroadcast;
    private final PromptTaskBuilder promptTaskBuilder;
    private final AiDecisionClient aiDecisionClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final String FALLBACK_SPEECH_TEXT = "「该角色沉默了一会，没有多说什么」";

    // ==================== 开局 ====================

    public Long startGame(Long roomId) {
        GameRoom room = roomService.getRoom(roomId);
        if (!"seat_filling".equals(room.getStatus())) {
            throw new IllegalStateException("房间当前状态不允许开局: " + room.getStatus());
        }
        List<SeatConfigItem> seats = roomService.readSeats(room.getSeatConfig());
        List<RoleBoardItem> roleBoard = roomService.readRoleBoard(room.getRoleBoard());
        RuleConfig ruleConfig = roomService.readRuleConfig(room.getRuleConfig());

        for (SeatConfigItem seat : seats) {
            if ("human".equals(seat.getOccupantType()) && seat.getUserId() == null) {
                throw new IllegalStateException("座位" + seat.getSeatNo() + "尚未加入真人玩家，无法开局");
            }
        }

        Map<String, RoleDefinition> roleDefs = new HashMap<>();
        for (RoleDefinition rd : roleDefinitionMapper.selectList(null)) {
            roleDefs.put(rd.getRoleKey(), rd);
        }

        List<String> roles = new ArrayList<>();
        for (RoleBoardItem item : roleBoard) {
            for (int i = 0; i < item.getCount(); i++) {
                roles.add(item.getRoleKey());
            }
        }
        Collections.shuffle(roles);

        room.setStatus("in_progress");
        roomMapper.updateById(room);

        GameSession session = new GameSession();
        session.setRoomId(roomId);
        session.setDayCount(0);
        session.setPhase("role_assigning");
        session.setStatus("running");
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        Long sessionId = session.getId();

        List<Long> usedCharacterIds = new ArrayList<>();
        List<RuntimePlayer> runtimePlayers = new ArrayList<>();
        seats.sort((a, b) -> a.getSeatNo() - b.getSeatNo());

        for (int i = 0; i < seats.size(); i++) {
            SeatConfigItem seat = seats.get(i);
            String roleKey = roles.get(i);
            RoleDefinition roleDef = roleDefs.get(roleKey);
            if (roleDef == null) {
                throw new IllegalStateException("未知角色定义: " + roleKey);
            }

            GamePlayer player = new GamePlayer();
            player.setSessionId(sessionId);
            player.setSeatNo(seat.getSeatNo());
            player.setInGameRole(roleKey);
            player.setCamp(roleDef.getCamp());
            player.setIsAlive(1);

            RuntimePlayer rp = new RuntimePlayer();
            rp.setSeatNo(seat.getSeatNo());
            rp.setInGameRole(roleKey);
            rp.setCamp(roleDef.getCamp());

            if ("human".equals(seat.getOccupantType())) {
                player.setIsAi(0);
                player.setUserId(seat.getUserId());
                AppUser user = appUserMapper.selectById(seat.getUserId());
                rp.setAi(false);
                rp.setUserId(seat.getUserId());
                rp.setDisplayName(user != null ? user.getNickname() : ("玩家" + seat.getUserId()));
            } else {
                player.setIsAi(1);
                Long characterId = seat.getCharacterId();
                if (characterId == null) {
                    characterId = pickAutoMatchCharacter(seat.getAutoMatchTier(), usedCharacterIds);
                }
                usedCharacterIds.add(characterId);
                player.setCharacterId(characterId);
                AiCharacter character = characterMapper.selectById(characterId);
                if (character == null) {
                    throw new IllegalStateException("AI角色不存在: " + characterId);
                }
                rp.setAi(true);
                rp.setCharacterId(characterId);
                rp.setDisplayName(character.getName());
                rp.setAvatarUrl(character.getAvatarUrl());
                rp.setBackstory(character.getBackstory());
                rp.setPersonalityVector(parseJsonLenient(character.getPersonalityVector()));
                rp.setSpeechStyle(parseJsonLenient(character.getSpeechStyle()));
            }
            playerMapper.insert(player);
            rp.setPlayerId(player.getId());
            runtimePlayers.add(rp);
        }

        RunningGame game = new RunningGame();
        game.setSessionId(sessionId);
        game.setRoomId(roomId);
        game.setRuleConfig(ruleConfig);
        game.setShowAiInnerMonologue(room.getShowAiInnerMonologue() != null && room.getShowAiInnerMonologue() == 1);
        game.setPlayers(runtimePlayers);
        game.setRoleDefinitions(roleDefs);
        game.setPhase("role_assigning");
        registry.register(game);

        for (RuntimePlayer rp : runtimePlayers) {
            eventBroadcast.record(game, rp.getPlayerId(), "role_assigned", null, null,
                    "你的身份是：" + roleDefs.get(rp.getInGameRole()).getRoleName(), "private_to_actor", false);
        }

        registry.executor().submit(() -> runGameLoop(sessionId));
        return sessionId;
    }

    private Long pickAutoMatchCharacter(String tier, List<Long> excludeIds) {
        LambdaQueryWrapper<AiCharacter> query = new LambdaQueryWrapper<AiCharacter>().eq(AiCharacter::getStatus, 1);
        if (tier != null && !tier.isBlank()) {
            query.eq(AiCharacter::getTierKey, tier);
        }
        List<AiCharacter> candidates = characterMapper.selectList(query);
        candidates.removeIf(c -> excludeIds.contains(c.getId()));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有可用的空闲AI角色（tier=" + tier + "）");
        }
        Collections.shuffle(candidates);
        return candidates.get(0).getId();
    }

    // ==================== 主循环 ====================

    private void runGameLoop(Long sessionId) {
        RunningGame game = registry.get(sessionId);
        try {
            if (game.getRuleConfig().isEnableSheriff()) {
                runSheriffElection(game);
            }
            while (!game.isEnded()) {
                game.setDayCount(game.getDayCount() + 1);
                runNightPhase(game);
                if (checkWinCondition(game)) break;
                runDayPhase(game);
                if (checkWinCondition(game)) break;
            }
            settleGame(game);
        } catch (GameForceEndedException e) {
            game.setEnded(true);
            game.setWinnerCamp("aborted");
            try {
                settleGame(game);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.error("对局{}异常终止", sessionId, e);
            try {
                settleGame(game);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 警长竞选（3.7节） ====================

    private void runSheriffElection(RunningGame game) {
        game.setPhase("sheriff_election");
        eventBroadcast.broadcastPhaseChange(game);

        List<RuntimePlayer> candidates = new ArrayList<>();
        for (RuntimePlayer p : game.alivePlayers()) {
            TaskSpec spec = TaskSpec.builder()
                    .actionType("sheriff_campaign_decision")
                    .instruction("是否参选警长？外向、有声望的角色更倾向参选。")
                    .allowAbstain(true)
                    .build();
            ActionOutcome outcome = obtainAction(game, p, spec);
            if (Boolean.TRUE.equals(outcome.getDecisionYes())) {
                candidates.add(p);
            }
        }

        if (candidates.isEmpty()) {
            eventBroadcast.record(game, systemActorId(game), "sheriff_campaign_speech", null, null,
                    "本局无人参选警长。", "public", false);
            return;
        }

        for (RuntimePlayer c : candidates) {
            TaskSpec spec = TaskSpec.builder()
                    .actionType("sheriff_campaign_speech")
                    .instruction("请发表竞选警长的发言，争取其他人的支持。")
                    .build();
            ActionOutcome outcome = obtainAction(game, c, spec);
            eventBroadcast.record(game, c.getPlayerId(), "sheriff_campaign_speech", null, null,
                    outcome.getContent(), "public", outcome.isFallback());
        }

        Map<Long, Integer> votes = new HashMap<>();
        for (RuntimePlayer voter : game.alivePlayers()) {
            List<RuntimePlayer> options = candidates.stream().filter(c -> !c.getPlayerId().equals(voter.getPlayerId())).toList();
            if (options.isEmpty()) continue;
            TaskSpec spec = TaskSpec.builder()
                    .actionType("sheriff_vote")
                    .instruction("请从竞选者中投出一票警长，不能投给自己。")
                    .candidateTargets(options)
                    .allowAbstain(true)
                    .build();
            ActionOutcome outcome = obtainAction(game, voter, spec);
            eventBroadcast.record(game, voter.getPlayerId(), "sheriff_vote", outcome.getTargetPlayerId(), null,
                    outcome.getContent(), "public", outcome.isFallback());
            if (outcome.getTargetPlayerId() != null) {
                votes.merge(outcome.getTargetPlayerId(), 1, Integer::sum);
            }
        }

        Optional<Map.Entry<Long, Integer>> winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if (winner.isPresent()) {
            RuntimePlayer sheriff = game.byPlayerId(winner.get().getKey());
            sheriff.setSheriff(true);
            game.setSheriffPlayerId(sheriff.getPlayerId());
            eventBroadcast.record(game, sheriff.getPlayerId(), "sheriff_elected", null, null,
                    sheriff.getDisplayName() + "当选警长。", "public", false);
        }
    }

    // ==================== 夜晚阶段（3.9.1节：收集式结算） ====================

    private void runNightPhase(RunningGame game) {
        game.setPhase("night");
        eventBroadcast.broadcastPhaseChange(game);
        announce(game, "天黑请闭眼。");

        List<RuntimePlayer> wolves = game.aliveByCamp("werewolf");
        Long wolfKillTarget = null;
        if (!wolves.isEmpty()) {
            announce(game, "狼人请睁眼，互相确认身份，选择今晚要击杀的目标。");
            wolfKillTarget = collectWerewolfKill(game);
            announce(game, "狼人请闭眼。");
        }

        // 女巫先于预言家行动（法官播报口径），见需求文档3.9.1节的收集式结算——
        // 顺序只影响播报节奏，是否存活/是否用药仍按夜间统一结算规则处理
        boolean healUsed = false;
        Long poisonTarget = null;
        RuntimePlayer witch = firstAliveByRole(game, "witch");
        if (witch != null) {
            announce(game, "女巫请睁眼。");
            boolean selfSaveBlocked = "first_night_only_disabled".equals(game.getRuleConfig().getWitchSelfSavePolicy())
                    && game.getDayCount() == 1 && wolfKillTarget != null && wolfKillTarget.equals(witch.getPlayerId());

            if (witch.isWitchHealAvailable() && wolfKillTarget != null && !selfSaveBlocked) {
                TaskSpec spec = TaskSpec.builder()
                        .actionType("night_action")
                        .instruction("今晚 " + game.byPlayerId(wolfKillTarget).getDisplayName() + " 被狼人袭击，是否使用解药救ta？")
                        .allowAbstain(true)
                        .build();
                ActionOutcome outcome = obtainAction(game, witch, spec);
                if (Boolean.TRUE.equals(outcome.getDecisionYes())) {
                    healUsed = true;
                    witch.setWitchHealAvailable(false);
                }
            }

            // 同一晚解药/毒药是否互斥：witch_dual_potion_same_night=true表示允许同时用，
            // 默认false则healUsed后本晚不再允许用毒，此前这里的取反写反了，已修正
            boolean mutexBlocked = healUsed && !game.getRuleConfig().isWitchDualPotionSameNight();
            if (witch.isWitchPoisonAvailable() && !mutexBlocked) {
                List<RuntimePlayer> options = game.alivePlayers().stream()
                        .filter(p -> !p.getPlayerId().equals(witch.getPlayerId())).toList();
                if (!options.isEmpty()) {
                    TaskSpec spec = TaskSpec.builder()
                            .actionType("night_action")
                            .instruction("是否使用毒药毒死一名玩家？不使用请弃权。")
                            .candidateTargets(options)
                            .allowAbstain(true)
                            .build();
                    ActionOutcome outcome = obtainAction(game, witch, spec);
                    if (outcome.getTargetPlayerId() != null) {
                        poisonTarget = outcome.getTargetPlayerId();
                        witch.setWitchPoisonAvailable(false);
                    }
                }
            }
            announce(game, "女巫请闭眼。");
        }

        Long seerCheckTarget = null;
        RuntimePlayer seer = firstAliveByRole(game, "seer");
        if (seer != null) {
            announce(game, "预言家请睁眼，请选择一名玩家查验身份。");
            List<RuntimePlayer> options = game.alivePlayers().stream()
                    .filter(p -> !p.getPlayerId().equals(seer.getPlayerId())).toList();
            if (!options.isEmpty()) {
                TaskSpec spec = TaskSpec.builder()
                        .actionType("night_action")
                        .instruction("请选择一名玩家查验其真实阵营（好人/狼人）。")
                        .candidateTargets(options)
                        .build();
                ActionOutcome outcome = obtainAction(game, seer, spec);
                seerCheckTarget = outcome.getTargetPlayerId();
                if (seerCheckTarget != null) {
                    RuntimePlayer target = game.byPlayerId(seerCheckTarget);
                    String result = "werewolf".equals(target.getCamp()) ? "狼人" : "好人";
                    eventBroadcast.record(game, seer.getPlayerId(), "night_action", seerCheckTarget, null,
                            "查验结果：" + target.getDisplayName() + " 是【" + result + "】。", "private_to_actor", outcome.isFallback());
                }
            }
            announce(game, "预言家请闭眼。");
        }

        // ---- 统一结算，见3.9.1节 ----
        if (wolfKillTarget != null && !healUsed) {
            applyDeath(game, game.byPlayerId(wolfKillTarget), "wolf_kill");
        }
        if (poisonTarget != null) {
            applyDeath(game, game.byPlayerId(poisonTarget), "poison");
        }

        announce(game, "天亮了，请所有玩家睁眼。");
    }

    /** 法官/主持人播报：只报"轮到谁"，不泄露具体行动内容，见需求文档8.4节信息隔离原则 */
    private void announce(RunningGame game, String content) {
        eventBroadcast.record(game, systemActorId(game), "night_announcement", null, null, content, "public", false);
    }

    private Long collectWerewolfKill(RunningGame game) {
        List<RuntimePlayer> wolves = game.aliveByCamp("werewolf");
        if (wolves.isEmpty()) return null;
        Map<Long, Integer> votes = new HashMap<>();
        List<RuntimePlayer> options = game.alivePlayers();
        for (RuntimePlayer wolf : wolves) {
            TaskSpec spec = TaskSpec.builder()
                    .actionType("night_action")
                    .instruction("与狼队友商议后，选择今晚要击杀的目标。")
                    .candidateTargets(options)
                    .build();
            ActionOutcome outcome = obtainAction(game, wolf, spec);
            eventBroadcast.record(game, wolf.getPlayerId(), "night_action", outcome.getTargetPlayerId(), null,
                    outcome.getContent() != null ? outcome.getContent() : "投票击杀目标", "werewolf_team", outcome.isFallback());
            if (outcome.getTargetPlayerId() != null) {
                votes.merge(outcome.getTargetPlayerId(), 1, Integer::sum);
            }
        }
        if (votes.isEmpty()) return null;
        return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    // ==================== 白天阶段 ====================

    private void runDayPhase(RunningGame game) {
        game.setPhase("day_announce");
        eventBroadcast.broadcastPhaseChange(game);

        List<RuntimePlayer> diedTonight = game.getPlayers().stream()
                .filter(p -> !p.isAlive() && p.getEliminatedDay() != null && p.getEliminatedDay() == game.getDayCount())
                .toList();

        if (diedTonight.isEmpty()) {
            eventBroadcast.record(game, systemActorId(game), "death_announcement", null, null,
                    "昨夜是平安夜，无人死亡。", "public", false);
        } else {
            StringBuilder sb = new StringBuilder("昨夜死亡玩家：");
            for (RuntimePlayer p : diedTonight) sb.append(p.getDisplayName()).append(" ");
            eventBroadcast.record(game, systemActorId(game), "death_announcement", null, null, sb.toString().trim(), "public", false);
            for (RuntimePlayer p : diedTonight) {
                processDeath(game, p);
                if (checkWinCondition(game)) return;
            }
        }

        game.setPhase("day_discuss");
        eventBroadcast.broadcastPhaseChange(game);
        for (RuntimePlayer speaker : speakingOrder(game)) {
            // 自爆只在白天发言环节可发动，见需求文档新增规则：不能在投票/遗言环节自爆——
            // 结构上本来就只有这个循环会问到"是否自爆"，天然满足这条约束
            if ("werewolf".equals(speaker.getCamp())) {
                TaskSpec destructSpec = TaskSpec.builder()
                        .actionType("self_destruct_decision")
                        .instruction("轮到你发言了。你可以选择现在自爆：立即结束白天讨论、直接进入黑夜，你会当场出局、不能再投票也没有遗言机会。"
                                + "如果决定自爆，请说出你的自爆宣言；如果不自爆，就当作本轮的正常发言，分析局面、表明立场。")
                        .allowAbstain(true)
                        .build();
                ActionOutcome destructOutcome = obtainAction(game, speaker, destructSpec);
                if (Boolean.TRUE.equals(destructOutcome.getDecisionYes())) {
                    String statement = destructOutcome.getContent() != null && !destructOutcome.getContent().isBlank()
                            ? destructOutcome.getContent() : (speaker.getDisplayName() + "选择自爆！");
                    eventBroadcast.record(game, speaker.getPlayerId(), "self_destruct", null, null,
                            statement, "public", destructOutcome.isFallback());
                    applyDeath(game, speaker, "self_destruct");
                    processDeath(game, speaker);
                    return; // 自爆立即结束白天，跳过剩余发言与投票，直接进入下一晚（胜负判定交给runGameLoop的循环体）
                }
                if (destructOutcome.getClaimedRole() != null) {
                    speaker.setLastClaimedRole(destructOutcome.getClaimedRole());
                }
                eventBroadcast.record(game, speaker.getPlayerId(), "speech", null, destructOutcome.getClaimedRole(),
                        destructOutcome.getContent(), "public", destructOutcome.isFallback());
                continue;
            }

            TaskSpec spec = TaskSpec.builder()
                    .actionType("speech")
                    .instruction("请发表你的看法：分析局面、表明立场，可以选择隐藏或公开自己的身份。")
                    .build();
            ActionOutcome outcome = obtainAction(game, speaker, spec);
            if (outcome.getClaimedRole() != null) {
                speaker.setLastClaimedRole(outcome.getClaimedRole());
            }
            eventBroadcast.record(game, speaker.getPlayerId(), "speech", null, outcome.getClaimedRole(),
                    outcome.getContent(), "public", outcome.isFallback());
        }

        game.setPhase("day_vote");
        eventBroadcast.broadcastPhaseChange(game);
        runVoteRound(game, game.alivePlayers(), false);
    }

    private List<RuntimePlayer> speakingOrder(RunningGame game) {
        List<RuntimePlayer> alive = game.alivePlayers();
        if (game.getSheriffPlayerId() == null) return alive;
        int startSeat = alive.stream().filter(RuntimePlayer::isSheriff).findFirst().map(RuntimePlayer::getSeatNo).orElse(-1);
        if (startSeat < 0) return alive;
        List<RuntimePlayer> ordered = new ArrayList<>();
        List<RuntimePlayer> after = alive.stream().filter(p -> p.getSeatNo() > startSeat).toList();
        List<RuntimePlayer> before = alive.stream().filter(p -> p.getSeatNo() <= startSeat).toList();
        ordered.addAll(after);
        ordered.addAll(before);
        return ordered;
    }

    /** @return 是否产生了出局结果 */
    private boolean runVoteRound(RunningGame game, List<RuntimePlayer> voters, boolean isPkRevote) {
        List<RuntimePlayer> candidates = isPkRevote ? null : game.alivePlayers();
        Map<Long, Double> weighted = new LinkedHashMap<>();
        for (RuntimePlayer voter : voters) {
            if (!voter.isCanVote()) {
                continue; // 白痴翻牌后永久失去投票权，见新增规则
            }
            List<RuntimePlayer> options = (isPkRevote ? currentPkCandidates : candidates).stream()
                    .filter(p -> !p.getPlayerId().equals(voter.getPlayerId())).toList();
            if (options.isEmpty()) continue;
            TaskSpec spec = TaskSpec.builder()
                    .actionType("vote")
                    .instruction(isPkRevote ? "请在PK候选人中重新投票，若都不选则弃权。" : "请投票选出你认为最可疑的人（弃权也可以）。")
                    .candidateTargets(options)
                    .allowAbstain(true)
                    .build();
            ActionOutcome outcome = obtainAction(game, voter, spec);
            eventBroadcast.record(game, voter.getPlayerId(), isPkRevote ? "pk_revote" : "vote", outcome.getTargetPlayerId(), null,
                    outcome.getContent(), "public", outcome.isFallback());
            if (outcome.getTargetPlayerId() != null) {
                double weight = voter.getPlayerId().equals(game.getSheriffPlayerId())
                        ? game.getRuleConfig().getSheriffVoteWeight() : 1.0;
                weighted.merge(outcome.getTargetPlayerId(), weight, Double::sum);
            }
        }

        if (weighted.isEmpty()) {
            eventBroadcast.record(game, systemActorId(game), "vote_result", null, null, "无人得票，本日无人出局。", "public", false);
            return false;
        }
        double max = Collections.max(weighted.values());
        List<Long> topVoted = weighted.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();

        if (topVoted.size() == 1) {
            RuntimePlayer target = game.byPlayerId(topVoted.get(0));
            eventBroadcast.record(game, systemActorId(game), "vote_result", target.getPlayerId(), null,
                    target.getDisplayName() + " 被投票出局。", "public", false);
            if ("idiot".equals(target.getInGameRole())) {
                handleIdiotVotedOut(game, target);
            } else {
                applyDeath(game, target, "vote_out");
                processDeath(game, target);
            }
            return true;
        }

        if (isPkRevote) {
            eventBroadcast.record(game, systemActorId(game), "vote_result", null, null, "PK再次平票，本日无人出局。", "public", false);
            return false;
        }

        // 平票 -> PK流程，见3.9.1/8.6节
        List<RuntimePlayer> tied = topVoted.stream().map(game::byPlayerId).toList();
        currentPkCandidates = tied;
        game.setPhase("day_pk_revote");
        eventBroadcast.broadcastPhaseChange(game);
        for (RuntimePlayer c : tied) {
            TaskSpec spec = TaskSpec.builder().actionType("pk_speech")
                    .instruction("请为自己辩护，试图说服其他人不要投你出局。").build();
            ActionOutcome outcome = obtainAction(game, c, spec);
            eventBroadcast.record(game, c.getPlayerId(), "pk_speech", null, null, outcome.getContent(), "public", outcome.isFallback());
        }
        List<RuntimePlayer> nonPkVoters = game.alivePlayers().stream()
                .filter(p -> tied.stream().noneMatch(t -> t.getPlayerId().equals(p.getPlayerId()))).toList();
        boolean result = runVoteRound(game, nonPkVoters, true);
        currentPkCandidates = null;
        return result;
    }

    // runVoteRound helper state（PK候选人集合，方法内递归传参更繁琐，改用短生命周期字段）
    private List<RuntimePlayer> currentPkCandidates;

    // ==================== 死亡触发链：遗言 -> 警徽 -> 死亡触发技能（3.8/3.9.2节） ====================

    private void applyDeath(RunningGame game, RuntimePlayer player, String cause) {
        player.setAlive(false);
        player.setEliminatedDay(game.getDayCount());
        player.setEliminatedReason(cause);
        GamePlayer db = playerMapper.selectById(player.getPlayerId());
        db.setIsAlive(0);
        db.setEliminatedDay(game.getDayCount());
        db.setEliminatedReason(cause);
        playerMapper.updateById(db);
    }

    private void processDeath(RunningGame game, RuntimePlayer player) {
        String cause = player.getEliminatedReason();
        if (computeLastWordsEligibility(game, cause)) {
            TaskSpec spec = TaskSpec.builder().actionType("last_words")
                    .instruction("这是你在本局的最后一次发言，可以考虑是否公布查验结果或嫌疑名单。").build();
            ActionOutcome outcome = obtainAction(game, player, spec);
            eventBroadcast.record(game, player.getPlayerId(), "last_words", null, outcome.getClaimedRole(),
                    outcome.getContent(), "public", outcome.isFallback());
        }

        // 警徽移交/毁徽只认"警长死亡"事件本身，不看死因，见3.9.2节；
        // 但自爆是例外——警上自爆直接强制毁徽，不给选择机会，见12人黄金标准局参考规则
        if (player.isSheriff()) {
            if ("self_destruct".equals(cause)) {
                forceDestroyBadge(game, player);
            } else {
                handleBadgeTransfer(game, player);
            }
        }

        // 死亡触发型技能（猎人/狼王等），数据驱动判断，见7.6节 trigger_action
        RoleDefinition roleDef = game.getRoleDefinitions() != null ? game.getRoleDefinitions().get(player.getInGameRole()) : null;
        if (roleDef != null && !player.isSkillTriggered()) {
            Map<String, Object> triggerAction = extractTriggerAction(roleDef.getSkillConfig());
            if (triggerAction != null && "on_death".equals(triggerAction.get("trigger_event"))
                    && "shoot".equals(triggerAction.get("action_type"))) {
                String condition = (String) triggerAction.get("condition");
                if (TriggerConditionEvaluator.evaluate(condition, cause)) {
                    player.setSkillTriggered(true);
                    List<RuntimePlayer> options = game.alivePlayers();
                    if (!options.isEmpty()) {
                        TaskSpec spec = TaskSpec.builder().actionType("night_action")
                                .instruction("你可以开枪带走一名玩家，也可以放弃。")
                                .candidateTargets(options).allowAbstain(true).build();
                        ActionOutcome outcome = obtainAction(game, player, spec);
                        if (outcome.getTargetPlayerId() != null) {
                            RuntimePlayer shot = game.byPlayerId(outcome.getTargetPlayerId());
                            eventBroadcast.record(game, player.getPlayerId(), "skill_trigger", shot.getPlayerId(), null,
                                    player.getDisplayName() + " 开枪带走了 " + shot.getDisplayName() + "。", "public", outcome.isFallback());
                            applyDeath(game, shot, "hunter_shot");
                            processDeath(game, shot);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTriggerAction(String skillConfigJson) {
        Object parsed = parseJsonLenient(skillConfigJson);
        if (!(parsed instanceof Map<?, ?> map)) return null;
        Object trigger = map.get("trigger_action");
        return trigger instanceof Map<?, ?> ? (Map<String, Object>) trigger : null;
    }

    /**
     * 遗言资格判断：放逐(vote_out)/猎人开枪(hunter_shot)始终有遗言，自爆(self_destruct)始终没有；
     * 夜间死亡(wolf_kill/poison)按rule_config.night_death_last_words_policy：
     * first_night_only=只有第1晚死亡有遗言、之后每晚都没有（竞技规则，见12人黄金标准局参考）；
     * 其余取值retain旧默认——刀杀总有遗言，毒杀单独看poison_death_has_last_words。
     */
    private boolean computeLastWordsEligibility(RunningGame game, String cause) {
        if ("self_destruct".equals(cause)) {
            return false;
        }
        if ("vote_out".equals(cause) || "hunter_shot".equals(cause)) {
            return true;
        }
        if ("first_night_only".equals(game.getRuleConfig().getNightDeathLastWordsPolicy())) {
            return game.getDayCount() == 1;
        }
        return !"poison".equals(cause) || game.getRuleConfig().isPoisonDeathHasLastWords();
    }

    /** 警上自爆：警徽强制流失，没有移交选择，见12人黄金标准局参考规则 */
    private void forceDestroyBadge(RunningGame game, RuntimePlayer deadSheriff) {
        deadSheriff.setSheriff(false);
        game.setSheriffPlayerId(null);
        eventBroadcast.record(game, deadSheriff.getPlayerId(), "badge_destroyed", null, null,
                "警长自爆，警徽流失，本局永久无警长。", "public", false);
    }

    /**
     * 白痴被投票出局：翻牌免死，永久失去投票权，但保留发言权与警长身份（若有）。
     * 只对"放逐"这一种死法免疫——夜晚被刀/被毒依然正常死亡，走applyDeath/processDeath的常规流程。
     */
    private void handleIdiotVotedOut(RunningGame game, RuntimePlayer target) {
        target.setCanVote(false);
        target.setRevealed(true);
        GamePlayer db = playerMapper.selectById(target.getPlayerId());
        db.setIsRevealed(1);
        playerMapper.updateById(db);
        eventBroadcast.record(game, target.getPlayerId(), "idiot_revealed", null, "idiot",
                target.getDisplayName() + " 翻开了身份牌——白痴！免于出局，但从此永久失去投票权。", "public", false);
    }

    private void handleBadgeTransfer(RunningGame game, RuntimePlayer deadSheriff) {
        List<RuntimePlayer> options = game.alivePlayers();
        deadSheriff.setSheriff(false);
        game.setSheriffPlayerId(null);
        if (options.isEmpty()) return;
        TaskSpec spec = TaskSpec.builder().actionType("badge_transfer")
                .instruction("请选择将警徽移交给谁，或选择毁徽（弃权视为毁徽）。")
                .candidateTargets(options).allowAbstain(true).build();
        ActionOutcome outcome = obtainAction(game, deadSheriff, spec);
        if (outcome.getTargetPlayerId() != null) {
            RuntimePlayer newSheriff = game.byPlayerId(outcome.getTargetPlayerId());
            newSheriff.setSheriff(true);
            game.setSheriffPlayerId(newSheriff.getPlayerId());
            eventBroadcast.record(game, deadSheriff.getPlayerId(), "badge_transfer", newSheriff.getPlayerId(), null,
                    "警徽移交给了 " + newSheriff.getDisplayName() + "。", "public", outcome.isFallback());
        } else {
            eventBroadcast.record(game, deadSheriff.getPlayerId(), "badge_destroyed", null, null,
                    "警徽被销毁，本局不再有警长。", "public", outcome.isFallback());
        }
    }

    // ==================== 胜负判定与结算 ====================

    /**
     * 标准"屠边"胜负判定（见12人黄金标准局参考规则）：
     * 好人阵营胜利 = 场上狼人全部出局；狼人阵营胜利 = 屠边——神职全灭 或 平民全灭，二者任一即可，
     * 不是简单比较"狼人数量是否反超好人总数"这种近似算法，此前版本的判定规则不够正统，已替换。
     */
    private boolean checkWinCondition(RunningGame game) {
        List<RuntimePlayer> alive = game.alivePlayers();
        long aliveWolves = alive.stream().filter(p -> "werewolf".equals(p.getCamp())).count();
        if (aliveWolves == 0) {
            game.setEnded(true);
            game.setWinnerCamp("good");
            game.setWinReason("wolves_eliminated");
            return true;
        }
        long aliveGods = alive.stream().filter(p -> !"werewolf".equals(p.getCamp()) && isGodRole(game, p)).count();
        long aliveVillagers = alive.stream().filter(p -> !"werewolf".equals(p.getCamp()) && !isGodRole(game, p)).count();
        if (aliveGods == 0) {
            game.setEnded(true);
            game.setWinnerCamp("werewolf");
            game.setWinReason("gods_eliminated");
            return true;
        }
        if (aliveVillagers == 0) {
            game.setEnded(true);
            game.setWinnerCamp("werewolf");
            game.setWinReason("villagers_eliminated");
            return true;
        }
        return false;
    }

    private boolean isGodRole(RunningGame game, RuntimePlayer p) {
        RoleDefinition def = game.getRoleDefinitions() != null ? game.getRoleDefinitions().get(p.getInGameRole()) : null;
        return def != null && def.getIsGodRole() != null && def.getIsGodRole() == 1;
    }

    private void settleGame(RunningGame game) {
        game.setPhase("settling");
        eventBroadcast.broadcastPhaseChange(game);

        String resultLine = switch (String.valueOf(game.getWinReason())) {
            case "wolves_eliminated" -> "场上所有狼人已全部被放逐/杀死出局，好人阵营屠狼成功，好人胜利！";
            case "gods_eliminated" -> "场上所有神职已全部出局，狼人屠边成功，狼人胜利！";
            case "villagers_eliminated" -> "场上所有平民已全部出局，狼人屠边成功，狼人胜利！";
            default -> "对局结束，获胜阵营：无（对局已被提前结束）。";
        };
        StringBuilder sb = new StringBuilder(resultLine + "真实身份公布：");
        for (RuntimePlayer p : game.getPlayers()) {
            sb.append(String.format("[%d号 %s 真实身份:%s claimed:%s] ", p.getSeatNo(), p.getDisplayName(),
                    p.getInGameRole(), p.getLastClaimedRole() != null ? p.getLastClaimedRole() : "-"));
        }
        eventBroadcast.record(game, systemActorId(game), "settlement", null, null, sb.toString(), "public", false);

        GameSession session = sessionMapper.selectById(game.getSessionId());
        session.setStatus("settled");
        session.setWinnerCamp(game.getWinnerCamp());
        session.setDayCount(game.getDayCount());
        session.setPhase("settled");
        session.setSheriffPlayerId(game.getSheriffPlayerId());
        session.setEndedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        GameRoom room = roomMapper.selectById(game.getRoomId());
        room.setStatus("settled");
        roomMapper.updateById(room);
    }

    // ==================== 通用：获取一次决策（AI调用 或 等待人类提交），含超时兜底（8.7/8.8节） ====================

    private ActionOutcome obtainAction(RunningGame game, RuntimePlayer player, TaskSpec spec) {
        waitWhilePaused(game);
        if (game.isForceEnded()) {
            throw new GameForceEndedException();
        }
        RuleConfig rc = game.getRuleConfig();
        if (player.isAi()) {
            for (int attempt = 0; attempt <= rc.getMaxLlmRetry(); attempt++) {
                try {
                    AiDecideRequest request = promptTaskBuilder.build(game, player, spec);
                    AiDecideResponse resp = aiDecisionClient.decide(request);
                    ActionOutcome outcome = new ActionOutcome();
                    outcome.setContent(resp.getContent());
                    outcome.setTargetPlayerId(resp.getTargetPlayerId());
                    outcome.setClaimedRole(resp.getClaimedRole());
                    outcome.setDecisionYes(resp.getDecisionYes());
                    outcome.setInnerMonologue(resp.getInnerMonologue());
                    if (game.isShowAiInnerMonologue() && resp.getInnerMonologue() != null) {
                        eventBroadcast.record(game, player.getPlayerId(), "inner_monologue", null, null,
                                resp.getInnerMonologue(), "god_view_only", false);
                    }
                    return outcome;
                } catch (Exception e) {
                    log.warn("AI回合失败，第{}次重试, player={}, action={}", attempt + 1, player.getPlayerId(), spec.getActionType());
                }
            }
            return fallbackFor(spec);
        } else {
            CompletableFuture<ActionSubmission> future = new CompletableFuture<>();
            game.getPendingActions().put(player.getPlayerId(), future);
            game.getPendingViews().put(player.getPlayerId(), buildPendingView(spec, rc.getTurnTimeoutSeconds()));
            try {
                ActionSubmission sub = future.get(rc.getTurnTimeoutSeconds(), TimeUnit.SECONDS);
                if (game.isForceEnded()) {
                    throw new GameForceEndedException();
                }
                ActionOutcome outcome = new ActionOutcome();
                outcome.setContent(sub.getContent());
                outcome.setTargetPlayerId(sub.getTargetPlayerId());
                outcome.setClaimedRole(sub.getClaimedRole());
                outcome.setDecisionYes(sub.getDecisionYes());
                return outcome;
            } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
                return fallbackFor(spec);
            } finally {
                game.getPendingActions().remove(player.getPlayerId());
                game.getPendingViews().remove(player.getPlayerId());
            }
        }
    }

    /**
     * 暂停在每次决策的起点生效——不打断正在进行中的单次AI调用/人类等待，
     * 只是让"下一回合"先不开始，直到被恢复或强制结束。
     */
    private void waitWhilePaused(RunningGame game) {
        while (game.isPaused() && !game.isForceEnded()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 8.7/8.8节：生成失败或超时后的安全兜底动作 */
    private ActionOutcome fallbackFor(TaskSpec spec) {
        ActionOutcome outcome = new ActionOutcome();
        outcome.setFallback(true);
        String type = spec.getActionType();
        boolean isSpeechLike = type.equals("speech") || type.equals("last_words")
                || type.equals("sheriff_campaign_speech") || type.equals("pk_speech");
        if (isSpeechLike) {
            outcome.setContent(FALLBACK_SPEECH_TEXT);
            return outcome;
        }
        // vote/night_action/badge_transfer/sheriff_vote/sheriff_campaign_decision 一律安全弃权，见8.8节
        outcome.setContent(FALLBACK_SPEECH_TEXT);
        outcome.setTargetPlayerId(null);
        outcome.setDecisionYes(false);
        return outcome;
    }

    private com.wolf.backend.dto.PendingActionView buildPendingView(TaskSpec spec, int timeoutSeconds) {
        com.wolf.backend.dto.PendingActionView view = new com.wolf.backend.dto.PendingActionView();
        view.setActionType(spec.getActionType());
        view.setPromptHint(spec.getInstruction());
        view.setTimeoutSeconds(timeoutSeconds);
        if (spec.getCandidateTargets() != null) {
            view.setCandidateTargetPlayerIds(spec.getCandidateTargets().stream().map(RuntimePlayer::getPlayerId).toList());
        }
        return view;
    }

    private RuntimePlayer firstAliveByRole(RunningGame game, String roleKey) {
        return game.alivePlayers().stream().filter(p -> roleKey.equals(p.getInGameRole())).findFirst().orElse(null);
    }

    /** 系统旁白类事件的占位actor：取当前存活的最小seat玩家id，仅用于满足actor_player_id NOT NULL约束 */
    private Long systemActorId(RunningGame game) {
        return game.getPlayers().get(0).getPlayerId();
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonLenient(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    /** 供 SessionController 提交人类玩家的动作 */
    public void submitAction(Long sessionId, ActionSubmission submission) {
        RunningGame game = registry.get(sessionId);
        if (game == null) {
            throw new IllegalArgumentException("对局不存在或已结束: " + sessionId);
        }
        CompletableFuture<ActionSubmission> future = game.getPendingActions().get(submission.getPlayerId());
        if (future == null) {
            throw new IllegalStateException("当前不是该玩家的行动回合");
        }
        future.complete(submission);
    }

    /**
     * 暂停对局。从下一次决策开始生效（正在进行中的单次AI调用/人类等待不会被打断），
     * 见文档MVP扩展：房主对局中控制。当前未做房主鉴权，任何能看到该局的调用方都可暂停/恢复/结束。
     */
    public void pauseGame(Long sessionId) {
        RunningGame game = requireRunningGame(sessionId);
        if (game.isPaused()) {
            return;
        }
        game.setPaused(true);
        eventBroadcast.record(game, systemActorId(game), "game_paused", null, null, "对局已暂停。", "public", false);
    }

    public void resumeGame(Long sessionId) {
        RunningGame game = requireRunningGame(sessionId);
        if (!game.isPaused()) {
            return;
        }
        game.setPaused(false);
        eventBroadcast.record(game, systemActorId(game), "game_resumed", null, null, "对局已恢复。", "public", false);
    }

    /**
     * 强制结束对局。立即唤醒可能正在等待人类提交的回合（不用干等到超时），
     * 正在进行中的单次AI调用会自然跑完，随后在下一个检查点（obtainAction开头）抛出
     * GameForceEndedException中止整条调用链，见类注释；因此结束是"很快"而非"瞬时"。
     */
    public void forceEndGame(Long sessionId) {
        RunningGame game = requireRunningGame(sessionId);
        game.setForceEnded(true);
        game.setPaused(false);
        for (CompletableFuture<ActionSubmission> future : game.getPendingActions().values()) {
            future.complete(new ActionSubmission());
        }
    }

    private RunningGame requireRunningGame(Long sessionId) {
        RunningGame game = registry.get(sessionId);
        if (game == null) {
            throw new IllegalArgumentException("对局不存在或已结束: " + sessionId);
        }
        return game;
    }

    public RunningGame getRunningGame(Long sessionId) {
        return registry.get(sessionId);
    }
}
