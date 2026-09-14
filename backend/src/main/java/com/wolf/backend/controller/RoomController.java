package com.wolf.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wolf.backend.dto.CreateRoomRequest;
import com.wolf.backend.dto.JoinRoomRequest;
import com.wolf.backend.dto.SeatConfigItem;
import com.wolf.backend.engine.GameEngineService;
import com.wolf.backend.entity.GameRoom;
import com.wolf.backend.entity.GameSession;
import com.wolf.backend.mapper.GameSessionMapper;
import com.wolf.backend.service.RoomService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 对应文档17节 /api/rooms 相关接口 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final GameEngineService gameEngineService;
    private final GameSessionMapper sessionMapper;

    @PostMapping
    public GameRoom createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }

    @GetMapping("/{roomId}")
    public GameRoom getRoom(@PathVariable Long roomId) {
        return roomService.getRoom(roomId);
    }

    @PostMapping("/{roomId}/join")
    public SeatConfigItem join(@PathVariable Long roomId, @RequestBody JoinRoomRequest request) {
        return roomService.join(roomId, request);
    }

    /** 供未点击"开始"的其他玩家轮询，得知本房间是否已经开局、对应的sessionId是多少 */
    @GetMapping("/{roomId}/session")
    public Map<String, Long> currentSession(@PathVariable Long roomId) {
        GameSession session = sessionMapper.selectOne(new LambdaQueryWrapper<GameSession>()
                .eq(GameSession::getRoomId, roomId).orderByDesc(GameSession::getId).last("limit 1"));
        Map<String, Long> result = new java.util.HashMap<>();
        result.put("sessionId", session != null ? session.getId() : null);
        return result;
    }

    @PostMapping("/{roomId}/start")
    public StartResponse start(@PathVariable Long roomId) {
        Long sessionId = gameEngineService.startGame(roomId);
        StartResponse resp = new StartResponse();
        resp.setSessionId(sessionId);
        return resp;
    }

    @Data
    public static class StartResponse {
        private Long sessionId;
    }
}
