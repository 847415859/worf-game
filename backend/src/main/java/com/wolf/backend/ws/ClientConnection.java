package com.wolf.backend.ws;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class ClientConnection {
    private WebSocketSession session;
    private Long sessionId;
    private Long playerId; // 该连接代表的玩家（人类玩家查看自己视角时携带），观众为空
    private boolean godView; // 仅当房间show_ai_inner_monologue=true时允许生效
}
