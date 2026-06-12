package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

@Component
public class LeaveRoomHandler implements GameActionHandler {

    private final MatchmakingService matchmakingService;

    public LeaveRoomHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.LEAVE_ROOM;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            // Не удалось определить пользователя – просто игнорируем
            sendRoomLeft(session);
            return;
        }

        // 1. Найти старую активную сессию (даже если текущая сессия другая)
        WebSocketSession activeSession = matchmakingService.getActiveSession(userId);
        if (activeSession != null) {
            GameRoom userRoom = matchmakingService.getRoomByPlayer(activeSession);
            if (userRoom != null) {
                // Удалить игрока из комнаты по старой сессии
                matchmakingService.removePlayer(activeSession);
            }
            // Очистить запись в activeUserSessions
            matchmakingService.unregisterActiveSession(userId);
        }

        // 2. Если текущая сессия привязана к какой-то комнате – также удалить
        GameRoom currentRoom = matchmakingService.getRoomByPlayer(session);
        if (currentRoom != null) {
            matchmakingService.removePlayer(session);
        }

        // 3. Отправить подтверждение клиенту
        sendRoomLeft(session);
    }

    private void sendRoomLeft(WebSocketSession session) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Вы вышли из комнаты");
        matchmakingService.sendDirectMessage(session,
            NetworkConfig.MessageBuilder.createMessage("ROOM_LEFT", response));
    }
}
