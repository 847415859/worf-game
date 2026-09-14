package com.wolf.backend.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 进行中对局的内存注册表，见文档6.4节：会话级运行时状态必须以session_id隔离，不做角色级缓存 */
@Component
public class GameRegistry {
    private final ConcurrentHashMap<Long, RunningGame> games = new ConcurrentHashMap<>();
    private final ExecutorService loopExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "game-loop-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    public void register(RunningGame game) {
        games.put(game.getSessionId(), game);
    }

    public RunningGame get(Long sessionId) {
        return games.get(sessionId);
    }

    public void remove(Long sessionId) {
        games.remove(sessionId);
    }

    public ExecutorService executor() {
        return loopExecutor;
    }
}
