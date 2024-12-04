package com.example.WebSocketProject;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new RoomWebSocketHandler(), "/room").setAllowedOrigins("*");
    }
}

@Component
class RoomWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new HashSet<>();
    private WebSocketSession hostSession = null; // Track the host session
    private String lastVideoState = "VIDEO_PAUSE";
    private String lastSeekTime = "0";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (hostSession == null) {
            hostSession = session; // First user becomes host
            session.sendMessage(new TextMessage("You are the host."));
        } else {
            session.sendMessage(new TextMessage("Welcome to the room."));
            // Send the current video state to the new user
            session.sendMessage(new TextMessage(lastVideoState));
            session.sendMessage(new TextMessage("VIDEO_SEEK:" + lastSeekTime));
        }
        sessions.add(session);
        broadcast("A new user has joined the room. Total users: " + sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // Handle video control messages
        if (payload.startsWith("VIDEO_")) {
            if (session.equals(hostSession)) {
                handleVideoControl(payload);
            } else {
                session.sendMessage(new TextMessage("Error: Only the host can control the video."));
            }
        } else {
            // Handle chat messages
            broadcast("User message: " + payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        sessions.remove(session);

        if (session.equals(hostSession)) {
            hostSession = null; // Reset host if the host leaves
            broadcast("The host has left the room. A new host is needed.");
            if (!sessions.isEmpty()) {
                // Assign a new host
                WebSocketSession newHost = sessions.iterator().next();
                hostSession = newHost;
                newHost.sendMessage(new TextMessage("You are the host."));
            }
        }

        broadcast("A user has left the room. Total users: " + sessions.size());
    }

    private void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleVideoControl(String message) {
        if (message.startsWith("VIDEO_PLAY")) {
            lastVideoState = message;
            message += ":" + lastSeekTime; // Append the last seek time to VIDEO_PLAY
        } else if (message.startsWith("VIDEO_PAUSE")) {
            lastVideoState = message;
        } else if (message.startsWith("VIDEO_SEEK")) {
            lastSeekTime = message.split(":")[1];
        }
        broadcast(message);
    }
    
}

