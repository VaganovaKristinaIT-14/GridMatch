package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

@Component
public class TokenDeselectHandler implements GameActionHandler {

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.TOKEN_DESELECT;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        int playerId = room.getPlayerId(session);
        if (playerId == -1) return;
        String removedToken = room.deselectToken(playerId);
        if (removedToken != null) {
            room.getAvailableTokens().add(removedToken);
            System.out.println("[TOKEN_DESELECT] Игрок " + playerId + " отменил выбор фишки " + removedToken);
        } else {
            System.out.println("[TOKEN_DESELECT] У игрока " + playerId + " не было выбранной фишки");
        }

        // Оповещаем всех об обновлении списка выборов
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("tokenSelections", room.getPlayerTokens());
        updateData.put("availableTokens", room.getAvailableTokens());

        Map<String, Object> updateMsg = NetworkConfig.MessageBuilder.createMessage(
            "TOKEN_UPDATE", updateData
        );
        room.broadcast(updateMsg);
    }
}
