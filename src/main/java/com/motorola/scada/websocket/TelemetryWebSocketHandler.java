package com.motorola.scada.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client can send commands here (acknowledge alarms, etc.)
    }

    public void broadcast(String json) {
        Set<WebSocketSession> closedSessions = new HashSet<>();
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                } else {
                    closedSessions.add(session);
                }
            } catch (IOException e) {
                closedSessions.add(session);
            }
        });
        sessions.removeAll(closedSessions);
    }

    public int getConnectedClients() {
        return sessions.size();
    }
}
