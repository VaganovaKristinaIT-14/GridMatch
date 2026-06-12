package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

@Component
public class TokenSelectionHandler implements GameActionHandler {

    private final MatchmakingService matchmakingService;

    public TokenSelectionHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.TOKEN_SELECTION;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        System.out.println("[TOKEN_HANDLER] Получен выбор фишки. Доступные токены в комнате: " + room.getAvailableTokens());

        int playerId = room.getPlayerId(session);
        if (playerId == -1) {
            System.out.println("Ошибка: не удалось определить ID игрока");
            return;
        }

        // === ИЗМЕНЕНО: проверка фазы ===
        if (!"WAITING_FOR_TOKENS".equals(room.getGamePhase())) {
            System.out.println("Игрок " + playerId + " пытается выбрать фишку не в той фазе: " + room.getGamePhase());
            sendError(session, "Выбор фишек уже завершён");
            return;
        }

        String selectedToken = (String) data.get(NetworkConfig.Keys.SELECTED_TOKEN);
        if (selectedToken == null || selectedToken.isEmpty()) {
            sendError(session, "Не указана фишка");
            return;
        }

        // Пытаемся выбрать фишку (синхронизированный метод в GameRoom)
        boolean success = room.selectToken(playerId, selectedToken);

        if (!success) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put(NetworkConfig.Keys.MESSAGE, "Фишка " + selectedToken + " уже занята");
            // ИЗМЕНЕНО: добавили selectedToken в ответ
            errorData.put(NetworkConfig.Keys.SELECTED_TOKEN, selectedToken);
            errorData.put(NetworkConfig.Keys.AVAILABLE_TOKENS, room.getAvailableTokens());

            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                NetworkConfig.MessageType.TOKEN_UNAVAILABLE, errorData
            );
            sendDirectMessage(session, msg);
            return;
        }

        // Оповещаем ВСЕХ об обновлении списка выборов
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("tokenSelections", room.getPlayerTokens());

        Map<String, Object> updateMsg = NetworkConfig.MessageBuilder.createMessage(
            "TOKEN_UPDATE", updateData
        );
        room.broadcast(updateMsg);

        System.out.println("Игрок " + playerId + " выбрал фишку: " + selectedToken);

        // Проверяем, все ли игроки выбрали фишки
        if (room.allPlayersSelectedTokens()) {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if ("WAITING_FOR_TOKENS".equals(room.getGamePhase())) {
                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put(NetworkConfig.Keys.TOKEN_SELECTIONS, room.getPlayerTokens());
                        completeData.put(NetworkConfig.Keys.MESSAGE, "Все игроки выбрали фишки! Начинаем...");

                        Map<String, Object> completeMsg = NetworkConfig.MessageBuilder.createMessage(
                            NetworkConfig.MessageType.TOKEN_SELECTION_COMPLETE, completeData
                        );
                        room.broadcast(completeMsg);

                        room.setGamePhase("GAME_ACTIVE");
                        matchmakingService.startGame(room);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void sendError(WebSocketSession session, String errorText) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put(NetworkConfig.Keys.MESSAGE, errorText);

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.ERROR, errorData
        );
        sendDirectMessage(session, msg);
    }

    private void sendDirectMessage(WebSocketSession session, Map<String, Object> messageMap) {
        try {
            if (session.isOpen()) {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(messageMap);
                session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            }
        } catch (Exception e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }
}
