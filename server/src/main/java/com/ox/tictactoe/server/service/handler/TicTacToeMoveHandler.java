package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component // Обязательно, чтобы Spring нашел этот класс
public class TicTacToeMoveHandler implements GameActionHandler {

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.PLAYER_MOVE;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        // Формируем сообщение и пересылаем всем остальным в комнате
        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.PLAYER_MOVE, data
        );
        room.broadcastExcept(session, msg);
    }
}
