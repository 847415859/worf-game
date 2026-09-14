package com.wolf.backend.controller;

import com.wolf.backend.dto.ActionSubmission;
import com.wolf.backend.dto.ReplayView;
import com.wolf.backend.dto.SessionStateView;
import com.wolf.backend.engine.GameEngineService;
import com.wolf.backend.service.SessionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 对应文档17节 /api/sessions/{sessionId}/state 与 /actions */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionQueryService sessionQueryService;
    private final GameEngineService gameEngineService;

    @GetMapping("/{sessionId}/state")
    public SessionStateView state(@PathVariable Long sessionId,
                                   @RequestParam(required = false) Long playerId,
                                   @RequestParam(required = false, defaultValue = "false") boolean godView) {
        return sessionQueryService.getState(sessionId, playerId, godView);
    }

    @PostMapping("/{sessionId}/actions")
    public void submitAction(@PathVariable Long sessionId, @RequestBody ActionSubmission submission) {
        gameEngineService.submitAction(sessionId, submission);
    }

    @GetMapping("/{sessionId}/replay")
    public ReplayView replay(@PathVariable Long sessionId,
                              @RequestParam(required = false, defaultValue = "false") boolean godView) {
        return sessionQueryService.getReplay(sessionId, godView);
    }

    @PostMapping("/{sessionId}/pause")
    public void pause(@PathVariable Long sessionId) {
        gameEngineService.pauseGame(sessionId);
    }

    @PostMapping("/{sessionId}/resume")
    public void resume(@PathVariable Long sessionId) {
        gameEngineService.resumeGame(sessionId);
    }

    @PostMapping("/{sessionId}/end")
    public void end(@PathVariable Long sessionId) {
        gameEngineService.forceEndGame(sessionId);
    }
}
