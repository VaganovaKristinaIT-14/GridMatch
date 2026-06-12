package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map;

public interface GameActionHandler {
    // Возвращает тип сообщения, который этот класс умеет обрабатывать (например "PLAYER_MOVE")
    String getSupportedType();

    // Сама логика обработки
    void handle(WebSocketSession session, GameRoom room, Map<String, Object> data);
}
