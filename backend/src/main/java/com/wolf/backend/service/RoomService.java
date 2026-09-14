package com.wolf.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wolf.backend.dto.CreateRoomRequest;
import com.wolf.backend.dto.JoinRoomRequest;
import com.wolf.backend.dto.RoleBoardItem;
import com.wolf.backend.dto.RuleConfig;
import com.wolf.backend.dto.SeatConfigItem;
import com.wolf.backend.entity.AppUser;
import com.wolf.backend.entity.GameRoom;
import com.wolf.backend.mapper.AppUserMapper;
import com.wolf.backend.mapper.GameRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final GameRoomMapper roomMapper;
    private final AppUserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 对应文档17节 POST /api/rooms，body为3.2节的房间配置Schema */
    @Transactional
    public GameRoom createRoom(CreateRoomRequest req) {
        int roleBoardTotal = req.getRoleBoard().stream().mapToInt(RoleBoardItem::getCount).sum();
        if (roleBoardTotal != req.getSeats().size()) {
            throw new IllegalArgumentException("角色板总人数(" + roleBoardTotal + ")与席位数(" + req.getSeats().size() + ")不一致");
        }
        long distinctSeatNos = req.getSeats().stream().map(SeatConfigItem::getSeatNo).distinct().count();
        if (distinctSeatNos != req.getSeats().size()) {
            throw new IllegalArgumentException("seat_no存在重复");
        }

        boolean hasHumanSeat = req.getSeats().stream().anyMatch(s -> "human".equals(s.getOccupantType()));
        RuleConfig ruleConfig = req.getRuleConfig() != null ? req.getRuleConfig() : RuleConfig.defaultsForSeats(hasHumanSeat);

        GameRoom room = new GameRoom();
        room.setRoomName(req.getRoomName());
        room.setMode(req.getMode());
        room.setTemplateId(req.getTemplateId());
        room.setRoleBoard(writeJson(req.getRoleBoard()));
        room.setSeatConfig(writeJson(req.getSeats()));
        room.setRuleConfig(writeJson(ruleConfig));
        room.setSpectatorPublic(req.isSpectatorPublic() ? 1 : 0);
        // 人机混战局强制关闭内心OS公开，见文档3.2/8.4节，防止观众/其他玩家开天眼
        room.setShowAiInnerMonologue(!hasHumanSeat && req.isShowAiInnerMonologue() ? 1 : 0);
        room.setStatus("seat_filling");
        room.setCreatedBy(req.getCreatedBy());
        roomMapper.insert(room);
        return room;
    }

    /** 对应文档17节 POST /api/rooms/{roomId}/join */
    @Transactional
    public SeatConfigItem join(Long roomId, JoinRoomRequest req) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        if (!"seat_filling".equals(room.getStatus())) {
            throw new IllegalStateException("房间当前状态不允许加入: " + room.getStatus());
        }

        Long userId = req.getUserId();
        if (userId == null) {
            AppUser user = new AppUser();
            user.setNickname(req.getNickname() != null && !req.getNickname().isBlank() ? req.getNickname() : "玩家" + System.currentTimeMillis() % 10000);
            userMapper.insert(user);
            userId = user.getId();
        }

        List<SeatConfigItem> seats = readSeats(room.getSeatConfig());
        SeatConfigItem target = null;
        if (req.getSeatNo() != null) {
            target = seats.stream().filter(s -> s.getSeatNo() == req.getSeatNo()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("座位不存在: " + req.getSeatNo()));
            if (!"human".equals(target.getOccupantType())) {
                throw new IllegalArgumentException("该座位不是真人席位");
            }
            if (target.getUserId() != null && !target.getUserId().equals(userId)) {
                throw new IllegalStateException("该座位已被占用");
            }
        } else {
            Long finalUserId = userId;
            target = seats.stream()
                    .filter(s -> "human".equals(s.getOccupantType()) && (s.getUserId() == null || s.getUserId().equals(finalUserId)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("没有空闲的真人席位"));
        }
        target.setUserId(userId);

        room.setSeatConfig(writeJson(seats));
        roomMapper.updateById(room);

        SeatConfigItem result = new SeatConfigItem();
        result.setSeatNo(target.getSeatNo());
        result.setOccupantType(target.getOccupantType());
        result.setUserId(userId);
        return result;
    }

    public GameRoom getRoom(Long roomId) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在: " + roomId);
        }
        return room;
    }

    public List<SeatConfigItem> readSeats(String json) {
        return readJson(json, new TypeReference<>() {});
    }

    public List<RoleBoardItem> readRoleBoard(String json) {
        return readJson(json, new TypeReference<>() {});
    }

    public RuleConfig readRuleConfig(String json) {
        return readJson(json, RuleConfig.class);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON序列化失败", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON反序列化失败", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON反序列化失败", e);
        }
    }
}
