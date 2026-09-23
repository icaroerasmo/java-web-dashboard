package com.icaroerasmo.dashboard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icaroerasmo.dashboard.service.DetectionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
@RequiredArgsConstructor
public class DetectionWebSocketHandler extends TextWebSocketHandler {

    private final DetectionStateService detectionStateService;
    private final ObjectMapper objectMapper;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private String lastPayload = "";

    @Scheduled(fixedDelayString = "5000")
    void sweep() {
        String payload = payload();
        if (!payload.equals(lastPayload)) {
            lastPayload = payload;
            broadcast();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        sendSnapshot(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast() {
        String payload = payload();
        lastPayload = payload;
        for (WebSocketSession session : sessions) {
            send(session, payload);
        }
    }

    private List<Map<String, String>> snapshot() {
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> active = detectionStateService.activeDetections();
        for (Map.Entry<String, String> entry : active.entrySet()) {
            result.add(Map.of("cameraName", entry.getKey(), "label", entry.getValue()));
        }
        return result;
    }

    private String payload() {
        try {
            return objectMapper.writeValueAsString(snapshot());
        } catch (Exception e) {
            log.warn("Failed to serialize detection snapshot: {}", e.getMessage());
            return "[]";
        }
    }

    private void sendSnapshot(WebSocketSession session) {
        send(session, payload());
    }

    private void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("Failed to send detection snapshot to session: {}", e.getMessage());
        }
    }
}