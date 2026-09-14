package com.wolf.backend.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对应需求文档17节 /ws/sessions/{sessionId}：实时事件推送。
 * MVP简化：WebSocket网关直接落在核心业务后端内，未单独拆NestJS网关（见6.3节的取舍说明）。
 */
@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("/ws/sessions/(\\d+)");

    private final Map<Long, CopyOnWriteArrayList<ClientConnection>> connectionsBySession = new ConcurrentHashMap<>();
    private final Map<String, ClientConnection> connectionByWsId = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        Matcher matcher = SESSION_ID_PATTERN.matcher(path);
        if (!matcher.find()) {
            closeQuietly(session, "invalid path");
            return;
        }
        Long sessionId = Long.valueOf(matcher.group(1));
        Map<String, String> query = parseQuery(session);

        ClientConnection conn = new ClientConnection();
        conn.setSession(session);
        conn.setSessionId(sessionId);
        if (query.containsKey("playerId")) {
            try {
                conn.setPlayerId(Long.valueOf(query.get("playerId")));
            } catch (NumberFormatException ignored) {
            }
        }
        conn.setGodView("true".equalsIgnoreCase(query.get("godView")));

        connectionsBySession.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(conn);
        connectionByWsId.put(session.getId(), conn);
        log.info("WS连接建立 session={} playerId={} godView={}", sessionId, conn.getPlayerId(), conn.isGodView());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClientConnection conn = connectionByWsId.remove(session.getId());
        if (conn != null) {
            List<ClientConnection> list = connectionsBySession.get(conn.getSessionId());
            if (list != null) {
                list.remove(conn);
            }
        }
    }

    public List<ClientConnection> connectionsFor(Long sessionId) {
        return connectionsBySession.getOrDefault(sessionId, new CopyOnWriteArrayList<>());
    }

    public void sendTo(ClientConnection conn, String payload) {
        try {
            if (conn.getSession().isOpen()) {
                conn.getSession().sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("WS推送失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.BAD_DATA.withReason(reason));
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> parseQuery(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return Map.of();
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().toSingleValueMap();
    }
}
