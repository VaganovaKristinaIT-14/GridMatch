package com.ox.tictactoe.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.game.MiniGameEngine;
import com.ox.tictactoe.server.model.GameRoom;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingService {


    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, GameRoom> playerRoomMap = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> playerUserIdMap = new ConcurrentHashMap<>();
    private final Map<Long, WebSocketSession> activeUserSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final long INACTIVITY_TIMEOUT_MS = 180000; // 3 минуты
    // ==========================================
    // 1. ПОДКЛЮЧЕНИЕ И ПОИСК КОМНАТЫ
    // ==========================================
    public boolean joinRoom(WebSocketSession session, String roomId) {
        GameRoom room = rooms.get(roomId);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null && isUserInAnyGame(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put(NetworkConfig.Keys.MESSAGE, "Вы уже играете на другом устройстве");
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.ALREADY_IN_GAME, error);
            sendDirectMessage(session, msg);
            return false;
        }
        if (room == null) {
            // Комната не найдена
            Map<String, Object> error = new HashMap<>();
            error.put(NetworkConfig.Keys.MESSAGE, "Комната не найдена");
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                "LOBBY_NOT_FOUND", error
            );
            sendDirectMessage(session, msg);
            return false;
        }

        if (room.isFull()) {
            // Комната полная
            Map<String, Object> error = new HashMap<>();
            error.put(NetworkConfig.Keys.MESSAGE, "Комната уже заполнена");
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                "LOBBY_FULL", error
            );
            sendDirectMessage(session, msg);
            return false;
        }

        room.addPlayer(session);
        playerRoomMap.put(session, room);
        broadcastLobbyUpdate(room);
        System.out.println("[MATCHMAKING] Игрок " + session.getId() + " присоединился к лобби " + roomId);

        if (room.isFull()) {
            // Перед выбором фишек перемешиваем игроков, чтобы номера 1, 2, 3 раздались случайно
            room.shuffleAndAssignIds();

            // Комната заполнена → начинаем выбор фишек
            room.setGamePhase("WAITING_FOR_TOKENS");
            sendTokenSelectionRequest(room);
            startTokenSelectionTimer(room);
        }
        if (userId != null) {
            registerActiveSession(userId, session);
        }
        return true;
    }
    private void broadcastLobbyUpdate(GameRoom room) {
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (WebSocketSession s : room.getPlayers()) {
            Map<String, Object> pInfo = new HashMap<>();
            pInfo.put("id", room.getPlayerId(s));
            pInfo.put("name", "Player " + room.getPlayerId(s));
            playersList.add(pInfo);
        }

        Map<String, Object> data = new HashMap<>();
        data.put(NetworkConfig.Keys.PLAYERS_IN_LOBBY, playersList);
        data.put(NetworkConfig.Keys.PLAYER_COUNT, room.getMaxPlayers());
        data.put(NetworkConfig.Keys.MESSAGE, "Обновление списка игроков");

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.LOBBY_UPDATE, data
        );
        room.broadcast(msg);
    }
    public GameRoom createLobby(WebSocketSession session, int playerCount, int boardSize) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            WebSocketSession oldSession = activeUserSessions.get(userId);
            if (oldSession != null && playerRoomMap.get(oldSession) == null) {
                activeUserSessions.remove(userId);
                System.out.println("CREATE_LOBBY: очистил мёртвую запись для userId=" + userId);
            }
        }
        if (userId != null && isUserInAnyGame(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put(NetworkConfig.Keys.MESSAGE, "Вы уже играете на другом устройстве");
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.ALREADY_IN_GAME, error);            sendDirectMessage(session, msg);
            return null;
        }
        String roomId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(roomId, playerCount, boardSize);
        rooms.put(roomId, room);

        // Добавляем создателя в комнату
        room.addPlayer(session);
        playerRoomMap.put(session, room);
        if (userId != null) {
            registerActiveSession(userId, session);
        }
        // Отправляем создателю подтверждение
        Map<String, Object> data = new HashMap<>();
        data.put(NetworkConfig.Keys.ROOM_ID, roomId);
        data.put(NetworkConfig.Keys.PLAYER_COUNT, playerCount);
        data.put(NetworkConfig.Keys.BOARD_SIZE, boardSize);
        data.put(NetworkConfig.Keys.MESSAGE, "Лобби создано! Ожидаем игроков...");

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.LOBBY_CREATED, data
        );
        sendDirectMessage(session, msg);

        System.out.println("[MATCHMAKING] Создано лобби " + roomId + " на " + playerCount + " игроков, доска " + boardSize + "x" + boardSize);
        return room;
    }
    public boolean joinRandomLobby(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null && isUserInAnyGame(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put(NetworkConfig.Keys.MESSAGE, "Вы уже играете на другом устройстве");
            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.ALREADY_IN_GAME, error);            sendDirectMessage(session, msg);
            return false;
        }
        GameRoom bestRoom = null;
        int maxPlayersCount = -1;

        double bestFill = -1;
        for (GameRoom room : rooms.values()) {
            if (!room.isFull() && "WAITING_FOR_PLAYERS".equals(room.getGamePhase())) {
                double fill = (double) room.getPlayers().size() / room.getMaxPlayers();
                if (fill > bestFill) {
                    bestFill = fill;
                    bestRoom = room;
                }
            }
        }

        if (bestRoom != null) {
            return joinRoom(session, bestRoom.getRoomId());
        }

        // Свободных комнат нет – отправляем ошибку
        Map<String, Object> error = new HashMap<>();
        error.put(NetworkConfig.Keys.MESSAGE, "Нет свободных комнат");
        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage("LOBBY_NOT_FOUND", error);
        sendDirectMessage(session, msg);
        return false;
    }
    public boolean isUserInAnyGame(long userId) {
        WebSocketSession session = activeUserSessions.get(userId);
        if (session == null) return false;

        GameRoom room = playerRoomMap.get(session);
        // Если игрок всё ещё в комнате (даже с закрытой сессией) — считаем, что он в игре
        if (room != null && room.getPlayerId(session) != -1 && !room.isGameFinished()) {
            return true;
        }

        // Иначе — запись устарела, удаляем
        activeUserSessions.remove(userId);
        return false;
    }
    public void registerActiveSession(long userId, WebSocketSession session) {
        activeUserSessions.put(userId, session);
        System.out.println("[SESSION] Пользователь " + userId + " зарегистрирован в активной игре");
    }
    public void unregisterActiveSession(long userId) {
        activeUserSessions.remove(userId);
        System.out.println("[SESSION] Пользователь " + userId + " удалён из активных игр");
    }
    // ==========================================
    // 2. ЗАПРОС НА ВЫБОР ФИШЕК
    // ==========================================
    // В MatchmakingService.java
    private void sendTokenSelectionRequest(GameRoom room) {
        for (WebSocketSession session : room.getPlayers()) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", "Выберите свою фишку!");
            data.put("availableTokens", room.getAvailableTokens());
            data.put("yourId", room.getPlayerId(session));
            data.put("roomId", room.getRoomId());

            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                NetworkConfig.MessageType.TOKEN_SELECTION_REQUEST, data
            );
            sendDirectMessage(session, msg);
        }
    }

    // ==========================================
    // 3. ТАЙМЕР ДЛЯ АВТОМАТИЧЕСКОГО ВЫБОРА
    // ==========================================
    // В MatchmakingService.java (внутри логики таймера)
    private void startTokenSelectionTimer(GameRoom room) {
        new Thread(() -> {
            try {
                Thread.sleep(30000); // Ожидание 30 сек
                if ("WAITING_FOR_TOKENS".equals(room.getGamePhase())) {
                    System.out.println("[TIMER] Время выбора вышло для комнаты " + room.getRoomId());

                    // 1. Назначаем фишки тем, кто проспал
                    room.assignRandomTokensToRemainingPlayers();

                    // 2. Рассылаем уведомление о тайм-ауте с итоговыми фишками
                    Map<String, Object> data = new HashMap<>();
                    data.put("message", "Время вышло. Фишки назначены автоматически.");
                    data.put("tokenSelections", room.getPlayerTokens());

                    Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                        "TOKEN_TIMEOUT", data
                    );
                    room.broadcast(msg);

                    // 3. НОВОЕ: Ждем 8 секунд перед фактическим стартом игры
                    System.out.println("[TIMER] Задержка 5 секунд перед стартом...");
                    Thread.sleep(8000);

                    // 4. Стартуем игру
                    room.setGamePhase("GAME_ACTIVE");
                    startGame(room);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ==========================================
    // 4. СТАРТ МАТЧА
    // ==========================================
    public void startGame(GameRoom room) {

        if (room == null) return;
        if (room.getPlayers().size() < 2) {
            for (WebSocketSession session : room.getPlayers()) {
                Map<String, Object> error = new HashMap<>();
                error.put(NetworkConfig.Keys.MESSAGE, "Недостаточно игроков для начала игры");
                Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage("LOBBY_NOT_FOUND", error);
                sendDirectMessage(session, msg);

                playerRoomMap.remove(session);
                Long uid = (Long) session.getAttributes().get("userId");
                if (uid != null) unregisterActiveSession(uid);
            }
            rooms.remove(room.getRoomId());
            return;
        }
        // 1. Инициализируем очередь ходов (игрок 1 всегда первый)
        room.initTurnOrder();
        room.initBoardState();
        int starterId = room.getCurrentTurnPlayerId();
        room.updateLastActionTime(starterId);

        // 2. Рассылаем всем информацию о начале игры и о том, КТО ходит первым
        for (WebSocketSession session : room.getPlayers()) {
            Map<String, Object> data = new HashMap<>();
            data.put("yourId", room.getPlayerId(session));
            data.put("activePlayerId", starterId); // СТРОГО ID текущего ходящего
            data.put("roomId", room.getRoomId());
            data.put("boardSize", room.getBoardSize());
            data.put("turnTimeoutSeconds", INACTIVITY_TIMEOUT_MS / 1000); // 180 секунд
            List<Map<String, Object>> playersList = new ArrayList<>();
            for (WebSocketSession s : room.getPlayers()) {
                int pId = room.getPlayerId(s);
                Map<String, Object> pInfo = new HashMap<>();
                pInfo.put("id", pId);
                pInfo.put("name", "Player " + pId);
                pInfo.put("token", room.getPlayerToken(pId));
                playersList.add(pInfo);
            }
            data.put("players", playersList);

            Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
                NetworkConfig.MessageType.OPPONENT_FOUND, data
            );
            sendDirectMessage(session, msg);
        }

        System.out.println("[MATCHMAKING] Игра началась в комнате " + room.getRoomId() + ". Первый ход: " + starterId);
    }

    // ==========================================
    // 5. ОТКЛЮЧЕНИЕ ИГРОКА
    // ==========================================
    public void removePlayer(WebSocketSession session) {
        GameRoom room = playerRoomMap.get(session);
        if (room == null) return;

        int leftPlayerId = room.getPlayerId(session);
        String gamePhase = room.getGamePhase();
        Long userId = (Long) session.getAttributes().get("userId");
        MiniGameEngine currentGame = room.getCurrentGame();
        if (currentGame != null && !currentGame.isFinished()) {
            currentGame.onPlayerDisconnected(leftPlayerId, room);
        }
        if (userId != null) {
            // Проверяем, не осталось ли у этого userId других сессий в комнатах
            // (но так как один userId не может быть в двух комнатах, просто удаляем)
            unregisterActiveSession(userId);
        }
        boolean wasFull = room.isFull();

        // Удаляем игрока из комнаты
        room.removePlayer(session);
        playerRoomMap.remove(session);
        playerUserIdMap.remove(session);

        // Рассылаем остальным
        Map<String, Object> data = new HashMap<>();
        data.put("id", leftPlayerId);
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "PLAYER_LEFT");
        msg.put("data", data);

        for (WebSocketSession s : room.getPlayers()) {
            if (s.isOpen() && !s.getId().equals(session.getId())) {
                sendDirectMessage(s, msg);
            }
        }

        // ========== ЛОГИКА В ЗАВИСИМОСТИ ОТ ФАЗЫ ==========

        if ("WAITING_FOR_PLAYERS".equals(gamePhase)) {
            // Игрок вышел до начала выбора фишек
            if (room.getPlayers().size() < room.getMaxPlayers()) {
                System.out.println("[MATCHMAKING] Игрок " + leftPlayerId + " вышел из лобби. Ждём остальных...");
            }

            // Если комната опустела — удаляем её
            if (room.isEmpty()) {
                rooms.remove(room.getRoomId());
                System.out.println("[MATCHMAKING] Комната " + room.getRoomId() + " удалена (пустая)");
                return;
            }
            return;
        }

        if ("WAITING_FOR_TOKENS".equals(gamePhase)) {
            // Игрок вышел во время выбора фишек
            System.out.println("[MATCHMAKING] Игрок " + leftPlayerId + " вышел во время выбора фишек.");

            // 1. Удаляем фишку этого игрока и возвращаем её в доступные
            String removedToken = room.getPlayerToken(leftPlayerId);
            if (removedToken != null) {
                room.getPlayerTokens().remove(leftPlayerId);
                room.getAvailableTokens().add(removedToken);
                System.out.println("[MATCHMAKING] Фишка " + removedToken + " возвращена в пул");
            }

            // 2. Оповещаем оставшихся игроков об обновлении списка доступных фишек
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("tokenSelections", room.getPlayerTokens());
            updateData.put("availableTokens", room.getAvailableTokens());

            Map<String, Object> updateMsg = NetworkConfig.MessageBuilder.createMessage(
                "TOKEN_UPDATE", updateData
            );
            room.broadcast(updateMsg);

            // 3. Проверяем, все ли оставшиеся игроки выбрали фишки
            if (room.allPlayersSelectedTokens()) {
                System.out.println("[MATCHMAKING] Все оставшиеся игроки выбрали фишки. Запускаем игру.");

                Map<String, Object> completeData = new HashMap<>();
                completeData.put(NetworkConfig.Keys.TOKEN_SELECTIONS, room.getPlayerTokens());
                completeData.put(NetworkConfig.Keys.MESSAGE, "Все игроки выбрали фишки! Начинаем...");

                Map<String, Object> completeMsg = NetworkConfig.MessageBuilder.createMessage(
                    NetworkConfig.MessageType.TOKEN_SELECTION_COMPLETE, completeData
                );
                room.broadcast(completeMsg);

                room.setGamePhase("GAME_ACTIVE");
                startGame(room);
            }

            // Если комната опустела — удаляем её
            if (room.isEmpty()) {
                rooms.remove(room.getRoomId());
                System.out.println("[MATCHMAKING] Комната " + room.getRoomId() + " удалена (пустая)");
            }
            return;
        }

        // Если игра уже идёт (GAME_ACTIVE или MINIGAME_ACTIVE)
        if ("GAME_ACTIVE".equals(gamePhase) || "MINIGAME_ACTIVE".equals(gamePhase)) {
            room.removePlayerFromTurnOrder(leftPlayerId);

            if (room.getPlayers().size() <= 1) {
                // Остался только один игрок (или никого)
                int winnerId = room.getPlayers().isEmpty() ? 0 : room.getPlayerId(room.getPlayers().get(0));

                Map<String, Object> gameOverMsg = new HashMap<>();
                gameOverMsg.put("type", "GAME_OVER");
                gameOverMsg.put("data", Map.of("winnerId", winnerId, "reason", "opponent_left"));
                room.broadcast(gameOverMsg);
                for (WebSocketSession s : room.getPlayers()) {
                    Long uid = (Long) s.getAttributes().get("userId");
                    if (uid != null) {
                        unregisterActiveSession(uid);
                    }
                }
                // ВАЖНО: Удаляем ВСЕХ оставшихся игрокОВ из playerRoomMap
                for (WebSocketSession s : room.getPlayers()) {
                    playerRoomMap.remove(s);
                    playerUserIdMap.remove(s);
                }

                // Удаляем комнату
                rooms.remove(room.getRoomId());
                System.out.println("[MATCHMAKING] Игра завершена, комната удалена. Победитель: " + winnerId);
            } else {
                // Игра продолжается
                Map<String, Object> turnUpdate = new HashMap<>();
                turnUpdate.put("type", "TURN_UPDATE");
                turnUpdate.put("data", Map.of("activePlayerId", room.getCurrentTurnPlayerId()));
                room.broadcast(turnUpdate);
            }
        }

        if (room.isEmpty()) {
            rooms.remove(room.getRoomId());
            System.out.println("[MATCHMAKING] Комната " + room.getRoomId() + " удалена");
        }

    }
    public void disconnectPlayer(WebSocketSession session) {
        GameRoom room = playerRoomMap.get(session);
        if (room == null) return;
        int playerId = room.getPlayerId(session);
        if (playerId == -1) return;

        room.markPlayerDisconnected(playerId);
        System.out.println("[MATCHMAKING] Игрок " + playerId + " помечен как отключённый");
    }
    // ==========================================
    // 6. ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ==========================================
    public void sendDirectMessage(WebSocketSession session, Map<String, Object> messageMap) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(messageMap);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GameRoom getRoomByPlayer(WebSocketSession session) {
        return playerRoomMap.get(session);
    }

    public Long getUserId(WebSocketSession session) {
        return playerUserIdMap.get(session);
    }
    public Collection<GameRoom> getAllRooms() {
        return rooms.values();
    }
    public void checkInactivityTimeouts(GameRoom room) {
        if (room == null) return;
        if (!"GAME_ACTIVE".equals(room.getGamePhase())) return;

        int currentPlayerId = room.getCurrentTurnPlayerId();
        if (currentPlayerId == -1) return;

        if (room.isPlayerTimedOut(currentPlayerId, INACTIVITY_TIMEOUT_MS)) {
            System.out.println("[TIMEOUT] Игрок " + currentPlayerId + " бездействовал более " + (INACTIVITY_TIMEOUT_MS/1000) + " сек.");

            WebSocketSession timedOutSession = null;
            for (WebSocketSession s : room.getPlayers()) {
                if (room.getPlayerId(s) == currentPlayerId) {
                    timedOutSession = s;
                    break;
                }
            }

            if (timedOutSession != null) {
                Map<String, Object> timeoutMsg = new HashMap<>();
                timeoutMsg.put("type", "PLAYER_TIMEOUT");
                timeoutMsg.put("data", Map.of("playerId", currentPlayerId, "message", "Вы были удалены за бездействие"));
                sendDirectMessage(timedOutSession, timeoutMsg);

                removePlayer(timedOutSession);

                // После удаления обновляем таймер для следующего игрока
                int newCurrentPlayerId = room.getCurrentTurnPlayerId();
                if (newCurrentPlayerId != -1) {
                    room.updateLastActionTime(newCurrentPlayerId);
                    System.out.println("[TIMEOUT] Сброшен таймер для нового игрока " + newCurrentPlayerId);
                }
            }
        }
    }
    public String reconnectPlayer(String roomId, long userId, int playerId, WebSocketSession newSession) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return "Комната не найдена";
        }

        // Ищем старую сессию
        WebSocketSession oldSession = null;
        for (Map.Entry<WebSocketSession, Integer> entry : room.getPlayerIds().entrySet()) {
            if (entry.getValue() == playerId) {
                oldSession = entry.getKey();
                break;
            }
        }
        if (oldSession == null) {
            return "Старая сессия не найдена для playerId=" + playerId;
        }

        // Если запись о дисконнекте отсутствует, создаём её (чтобы дать игроку шанс переподключиться)
        if (!room.getDisconnectedSince().containsKey(playerId)) {
            room.markPlayerDisconnected(playerId);
            System.out.println("[RECONNECT] Принудительно помечен отключённым игрок " + playerId + " в комнате " + roomId);
        }

        // Проверяем, не истекло ли время переподключения (60 секунд)
        if (!room.canReconnect(playerId)) {
            return "Время переподключения истекло";
        }

        // Заменяем сессии
        room.getPlayers().remove(oldSession);
        room.getPlayers().add(newSession);
        playerRoomMap.remove(oldSession);
        playerRoomMap.put(newSession, room);
        room.getPlayerIds().remove(oldSession);
        room.getPlayerIds().put(newSession, playerId);
        playerUserIdMap.put(newSession, userId);
        activeUserSessions.put(userId, newSession);
        newSession.getAttributes().put("authenticated", true);
        newSession.getAttributes().put("userId", userId);
        room.getDisconnectedSince().remove(playerId); // очищаем запись

        if (oldSession.isOpen()) {
            try {
                oldSession.close();
            } catch (IOException e) {
                // игнорируем
            }
        }

        return null; // успех
    }
    @Scheduled(fixedDelay = 5000) // каждые 5 секунд
    public void cleanupDisconnectedPlayers() {
        for (GameRoom room : rooms.values()) {
            Collection<Integer> expired = room.getExpiredDisconnectedPlayers();
            for (int playerId : expired) {
                // Найти сессию по playerId
                WebSocketSession session = null;
                for (WebSocketSession s : room.getPlayers()) {
                    if (room.getPlayerId(s) == playerId) {
                        session = s;
                        break;
                    }
                }
                if (session != null) {
                    System.out.println("[MATCHMAKING] Игрок " + playerId + " не переподключился, удаляем");
                    removePlayer(session); // существующий метод
                }
            }
        }
    }
    public long getInactivityTimeoutMs() {
        return INACTIVITY_TIMEOUT_MS;
    }
    public WebSocketSession getActiveSession(long userId) {
        return activeUserSessions.get(userId);
    }
}
