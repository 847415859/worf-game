package com.wolf.backend.engine;

import com.wolf.backend.dto.ActionSubmission;
import com.wolf.backend.dto.PendingActionView;
import com.wolf.backend.dto.RuleConfig;
import com.wolf.backend.entity.RoleDefinition;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class RunningGame {
    private Long sessionId;
    private Long roomId;
    private RuleConfig ruleConfig;
    private boolean showAiInnerMonologue;
    private List<RuntimePlayer> players; // 按seatNo排序
    private Map<String, RoleDefinition> roleDefinitions; // role_key -> 定义，见7.6节

    private volatile int dayCount = 0;
    private volatile String phase = "role_assigning";
    private volatile boolean ended = false;
    private volatile String winnerCamp;
    // wolves_eliminated / gods_eliminated / villagers_eliminated，供结算文案区分具体屠边方式
    private volatile String winReason;

    // 房主/管理员的暂停与强制结束控制，见obtainAction()里的检查点
    private volatile boolean paused = false;
    private volatile boolean forceEnded = false;

    private volatile Long sheriffPlayerId;

    private final ConcurrentHashMap<Long, CompletableFuture<ActionSubmission>> pendingActions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingActionView> pendingViews = new ConcurrentHashMap<>();

    public RuntimePlayer byPlayerId(Long id) {
        return players.stream().filter(p -> p.getPlayerId().equals(id)).findFirst().orElse(null);
    }

    public Optional<RuntimePlayer> bySeat(int seat) {
        return players.stream().filter(p -> p.getSeatNo() == seat).findFirst();
    }

    public List<RuntimePlayer> alivePlayers() {
        return players.stream().filter(RuntimePlayer::isAlive).sorted((a, b) -> a.getSeatNo() - b.getSeatNo()).toList();
    }

    public List<RuntimePlayer> aliveByCamp(String camp) {
        return players.stream().filter(RuntimePlayer::isAlive).filter(p -> camp.equals(p.getCamp())).toList();
    }
}
