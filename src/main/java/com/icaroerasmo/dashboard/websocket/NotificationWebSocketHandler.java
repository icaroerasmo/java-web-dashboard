package com.icaroerasmo.dashboard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icaroerasmo.dashboard.messaging.NotificationSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes new notification summaries to connected dashboard clients so the
 * frontend can render the feed and trigger browser notifications in real time.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void push(NotificationSummary summary) {
        if (summary == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(summary);
            for (WebSocketSession session : sessions) {
                send(session, payload);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize notification summary: {}", e.getMessage());
        }
    }

    private void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("Failed to send notification to session: {}", e.getMessage());
        }
    }
}
