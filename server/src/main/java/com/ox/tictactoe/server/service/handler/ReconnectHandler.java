package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReconnectHandler implements GameActionHandler {

    private final MatchmakingService matchmakingService;

    public ReconnectHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public String getSupportedType() {
        return "RECONNECT";
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        String roomId = (String) data.get(NetworkConfig.Keys.ROOM_ID);
        Object userIdObj = data.get("userId");
        if (userIdObj == null) {
            sendReconnectFailed(session, "Не указан userId");
            return;
        }
        Long userId = null;
        if (userIdObj instanceof Number) {
            userId = ((Number) userIdObj).longValue();
        }
        if (userId == null) {
            sendReconnectFailed(session, "Неверный формат userId");
            return;
        }
        if (roomId == null) {
            sendReconnectFailed(session, "Не указана комната");
            return;
        }

        // НОВОЕ: извлекаем playerId
        Object playerIdObj = data.get("playerId");
        if (playerIdObj == null) {
            sendReconnectFailed(session, "Не указан playerId");
            return;
        }
        int playerId = ((Number) playerIdObj).intValue();

        String error = matchmakingService.reconnectPlayer(roomId, userId, playerId, session);
        if (error == null) {
            GameRoom restoredRoom = matchmakingService.getRoomByPlayer(session);
            if (restoredRoom == null) {
                sendReconnectFailed(session, "Ошибка восстановления комнаты");
                return;
            }
            int turnTimeoutSeconds = (int) (matchmakingService.getInactivityTimeoutMs() / 1000);
            Map<String, Object> state = buildRoomState(restoredRoom, userId, turnTimeoutSeconds);
            Map<String, Object> res = new HashMap<>();
            res.put("type", "RECONNECT_SUCCESS");
            res.put("data", state);
            matchmakingService.sendDirectMessage(session, res);
        } else {
            // 1. Удаляем пользователя из списка активных сессий, чтобы он мог заново войти
            matchmakingService.unregisterActiveSession(userId);

            // 2. Если сессия по какой-то причине всё ещё привязана к комнате – удаляем
            GameRoom anyRoom = matchmakingService.getRoomByPlayer(session);
            if (anyRoom != null) {
                matchmakingService.removePlayer(session);
            }


            sendReconnectFailed(session, error);
        }
    }

    private void sendReconnectFailed(WebSocketSession session, String message) {
        Map<String, Object> res = new HashMap<>();
        res.put("type", "RECONNECT_FAILED");
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        res.put("data", data);
        matchmakingService.sendDirectMessage(session, res);
    }

    private Map<String, Object> buildRoomState(GameRoom room, long userId, int turnTimeoutSeconds)
    {
        Map<String, Object> state = new HashMap<>();
        state.put("roomId", room.getRoomId());
        state.put("boardSize", room.getBoardSize());
        state.put("yourId", getPlayerIdByUserId(room, userId));
        state.put("activePlayerId", room.getCurrentTurnPlayerId());
        state.put("turnTimeoutSeconds", 180);
        int currentPlayerId = room.getCurrentTurnPlayerId();
        long lastAction = room.getLastActionTime(currentPlayerId);
        long elapsed = System.currentTimeMillis() - lastAction;
        float timeLeft = turnTimeoutSeconds - (elapsed / 1000f);
        if (timeLeft < 0) timeLeft = 0;
        state.put("timeLeftForCurrentTurn", timeLeft);
        List<Map<String, Object>> players = new ArrayList<>();
        for (WebSocketSession s : room.getPlayers()) {
            int pid = room.getPlayerId(s);
            Map<String, Object> p = new HashMap<>();
            p.put("id", pid);
            p.put("name", "Player " + pid);
            p.put("token", room.getPlayerToken(pid));
            players.add(p);
        }
        state.put("players", players);
        state.put("boardState", room.getBoardState());
        state.put("gamePhase", room.getGamePhase());

        if ("MINIGAME_ACTIVE".equals(room.getGamePhase()) && room.getCurrentGame() != null && !room.getCurrentGame().isFinished()) {
            Map<String, Object> mg = new HashMap<>();
            mg.put("gameType", room.getCurrentGame().getGameType());
            mg.put("pendingRow", room.getPendingRow());
            mg.put("pendingCol", room.getPendingCol());

            // Получаем playerId по userId
            int playerId = getPlayerIdByUserId(room, userId);
            Map<String, Object> playerState = room.getCurrentGame().getPlayerState(playerId);
            mg.put("state", playerState);

            state.put("minigame", mg);
        }
        if ("WAITING_FOR_TOKENS".equals(room.getGamePhase())) {
            state.put("availableTokens", room.getAvailableTokens());
            state.put("tokenSelections", room.getPlayerTokens());
        }
        return state;
    }

    private int getPlayerIdByUserId(GameRoom room, long userId) {
        for (WebSocketSession s : room.getPlayers()) {
            Object uidObj = s.getAttributes().get("userId");
            if (uidObj == null) continue;
            long uid = ((Number) uidObj).longValue();
            if (uid == userId) {
                return room.getPlayerId(s);
            }
        }
        return -1;
    }
}
