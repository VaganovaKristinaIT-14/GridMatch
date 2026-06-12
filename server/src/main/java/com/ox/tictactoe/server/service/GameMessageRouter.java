package com.ox.tictactoe.server.service;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.handler.GameActionHandler;
import com.ox.tictactoe.server.game.MiniGameEngine;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameMessageRouter {

    private final Map<String, GameActionHandler> handlers = new HashMap<>();
    private final MatchmakingService matchmakingService;

    public GameMessageRouter(List<GameActionHandler> handlerList, MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;

        for (GameActionHandler handler : handlerList) {
            System.out.println("Зарегистрирован обработчик: " + handler.getSupportedType());
            handlers.put(handler.getSupportedType(), handler);
        }
    }

    /**
     * Основной метод маршрутизации.
     * Принимает Object type, чтобы не падать при получении странных структур из Jackson.
     */
    public void route(Object type, WebSocketSession session, Map<String, Object> data) {
        // Превращаем в строку
        String typeStr = (type != null) ? String.valueOf(type) : "";

        // 1. Находим комнату игрока
        GameRoom room = matchmakingService.getRoomByPlayer(session);

        // Для сообщений лобби не нужна комната
        boolean isLobbyMessage = typeStr.equals("CREATE_LOBBY") ||
            typeStr.equals("JOIN_RANDOM_LOBBY") ||
            typeStr.equals("LEAVE_ROOM") ||
            typeStr.equals("RECONNECT");

        if (room == null && !isLobbyMessage) {
            System.out.println("Игрок " + session.getId() + " не в комнате");
            return;
        }

        // 2. ОСОБАЯ ОБРАБОТКА: сообщения для мини-игры
        if (typeStr.contains(NetworkConfig.MessageType.MINIGAME_ACTION)) {
            handleMiniGameMessage(session, room, data);
            return;
        }

        // 3. Поиск обычных обработчиков
        GameActionHandler handler = null;
        for (Map.Entry<String, GameActionHandler> entry : handlers.entrySet()) {
            if (typeStr.contains(entry.getKey())) {
                handler = entry.getValue();
                break;
            }
        }

        if (handler != null) {
            handler.handle(session, room, data);
        } else {
            System.out.println("Нет обработчика для типа: " + typeStr);
        }
    }

    /**
     * Обрабатывает сообщения, относящиеся к мини-игре
     */
    private void handleMiniGameMessage(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        MiniGameEngine game = room.getCurrentGame();

        if (game == null) {
            System.out.println("В комнате " + room.getRoomId() + " нет активной игры");
            return;
        }

        if (game.isFinished()) {
            System.out.println("Игра в комнате " + room.getRoomId() + " уже закончена");
            return;
        }

        int playerId = room.getPlayerId(session);
        room.updateLastActionTime(playerId);
        if (playerId == -1) {
            System.out.println("Не удалось определить ID игрока " + session.getId());
            return;
        }

        // Выводим в лог действие, чтобы видеть, что CLICK дошел
        Object action = data.get(NetworkConfig.Keys.ACTION);
        System.out.println("Игрок " + playerId + " в комнате " + room.getRoomId() +
            " совершил действие: " + action);

        // Передаём действие в логику игры (судье)
        game.handleAction(playerId, data, room);
    }
}
