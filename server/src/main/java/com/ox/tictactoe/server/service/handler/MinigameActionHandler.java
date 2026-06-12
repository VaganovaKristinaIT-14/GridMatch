package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MinigameActionHandler implements GameActionHandler {

    private final Set<String> finishedMiniGames = ConcurrentHashMap.newKeySet();

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.MINIGAME_ACTION;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        String action = (String) data.get("action");

        if ("CLICK".equals(action)) {
            // Кто первый нажал - тот и победил
            if (finishedMiniGames.add(room.getRoomId())) {
                System.out.println("В мини-игре победил: " + session.getId());

                Map<String, Object> resultData = new HashMap<>();
                resultData.put(NetworkConfig.Keys.MINIGAME_WINNER_ID, session.getId());

                Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                    NetworkConfig.MessageType.MINIGAME_RESULT, resultData
                );
                room.broadcast(msg);
            }
        } else {
            // Любое другое действие (не кнопка) просто пересылаем
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                NetworkConfig.MessageType.MINIGAME_ACTION, data
            );
            room.broadcastExcept(session, msg);
        }
    }
}
