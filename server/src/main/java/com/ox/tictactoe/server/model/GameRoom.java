package com.ox.tictactoe.server.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.game.MiniGameEngine;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameRoom {
    private final String roomId;
    private boolean gameFinished = false;
    private int maxPlayers;
    private int boardSize;
    private boolean isLobbyReady = false;
    private final Map<WebSocketSession, Integer> playerIds = new ConcurrentHashMap<>();
    private int nextPlayerId = 1;
    private final List<WebSocketSession> players = new CopyOnWriteArrayList<>();
    private MiniGameEngine currentGame;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Integer, String> playerTokens = new ConcurrentHashMap<>();
    private final List<String> availableTokens = new CopyOnWriteArrayList<>();

    private String gamePhase = "WAITING_FOR_PLAYERS";

    private List<Integer> turnOrder = new ArrayList<>();
    private int currentTurnIndex = 0;

    private int pendingRow = -1;
    private int pendingCol = -1;
    private int pendingAttackerId = -1;   // НОВОЕ ПОЛЕ
    private int[][] boardState;
    private final Map<Integer, Long> disconnectedSince = new ConcurrentHashMap<>();
    private static final long DISCONNECT_TIMEOUT_MS = 60000;


    private final Map<Integer, Long> lastActionTime = new ConcurrentHashMap<>();

    public GameRoom(String roomId, int maxPlayers, int boardSize) {
        this.roomId = roomId;
        this.maxPlayers = maxPlayers;
        this.boardSize = boardSize;
        this.availableTokens.addAll(Arrays.asList("SUN", "TREE", "KEY", "CROWN", "WOLF", "BIRD"));
        System.out.println("[ROOM] Создана комната: " + roomId + " | Игроков: " + maxPlayers + " | Доска: " + boardSize + "x" + boardSize);
        System.out.println("[ROOM] Токены инициализированы: " + availableTokens);
    }

    // Геттеры/сеттеры для pendingAttackerId
    public int getPendingAttackerId() { return pendingAttackerId; }
    public void setPendingAttackerId(int id) { this.pendingAttackerId = id; }

    public boolean isGameFinished() { return gameFinished; }
    public void setGameFinished(boolean finished) { this.gameFinished = finished; }
    public String getGamePhase() { return gamePhase; }
    public void setGamePhase(String gamePhase) { this.gamePhase = gamePhase; }

    public synchronized boolean selectToken(int playerId, String token) {
        if (playerTokens.containsKey(playerId)) {
            System.out.println("Игрок " + playerId + " уже выбрал фишку " + playerTokens.get(playerId) + ", повторный выбор отклонён");
            return false;
        }
        if (!availableTokens.contains(token)) return false;
        availableTokens.remove(token);
        playerTokens.put(playerId, token);
        System.out.println("Игрок " + playerId + " выбрал фишку: " + token);
        return true;
    }

    public boolean allPlayersSelectedTokens() {
        return playerTokens.size() >= maxPlayers;
    }

    public Map<Integer, String> getPlayerTokens() {
        return new HashMap<>(playerTokens);
    }

    public String getPlayerToken(int playerId) {
        return playerTokens.get(playerId);
    }

    public List<String> getAvailableTokens() {
        return new ArrayList<>(availableTokens);
    }

    public int getMaxPlayers() { return maxPlayers; }
    public int getBoardSize() { return boardSize; }
    public long getLastActionTime(int playerId) {
        return lastActionTime.getOrDefault(playerId, 0L);
    }
    public boolean isLobbyReady() { return isLobbyReady; }
    public void setLobbyReady(boolean ready) { isLobbyReady = ready; }

    public synchronized void assignRandomTokensToRemainingPlayers() {
        System.out.println("[AUTO-ASSIGN] Начало. availableTokens: " + availableTokens);
        for (WebSocketSession session : players) {
            int pId = getPlayerId(session);
            if (!playerTokens.containsKey(pId)) {
                if (!availableTokens.isEmpty()) {
                    String autoToken = availableTokens.remove(0);
                    playerTokens.put(pId, autoToken);
                    System.out.println("[AUTO-ASSIGN] Игроку " + pId + " назначен " + autoToken);
                } else {
                    System.err.println("[AUTO-ASSIGN] Нет доступных токенов для " + pId);
                }
            }
        }
    }

    public int getPendingRow() { return pendingRow; }
    public void setPendingRow(int pendingRow) { this.pendingRow = pendingRow; }
    public int getPendingCol() { return pendingCol; }
    public void setPendingCol(int pendingCol) { this.pendingCol = pendingCol; }
    public void clearPendingCell() {
        this.pendingRow = -1;
        this.pendingCol = -1;
        this.pendingAttackerId = -1;
    }

    public MiniGameEngine getCurrentGame() { return currentGame; }
    public void setCurrentGame(MiniGameEngine game) { this.currentGame = game; }
    public String getRoomId() { return roomId; }
    public List<WebSocketSession> getPlayers() { return players; }

    public void addPlayer(WebSocketSession session) {
        if (!isFull()) {
            players.add(session);
            playerIds.put(session, nextPlayerId);
            updateLastActionTime(nextPlayerId);
            System.out.println("Игроку " + session.getId() + " назначен ID: " + nextPlayerId);
            nextPlayerId++;
        }
    }

    public void removePlayer(WebSocketSession session) {
        int playerId = getPlayerId(session);
        if (playerId == -1) return;
        players.remove(session);
        playerIds.remove(session);
        String removedToken = playerTokens.remove(playerId);
        if ("WAITING_FOR_TOKENS".equals(gamePhase) && removedToken != null) {
            availableTokens.add(removedToken);
            System.out.println("[ROOM] Фишка " + removedToken + " возвращена в пул после выхода игрока " + playerId);
        }
        removePlayerFromTurnOrder(playerId);
        removePlayerFromTimeoutTracking(playerId);
    }

    public void shuffleAndAssignIds() {
        playerIds.clear();
        List<WebSocketSession> shuffledPlayers = new ArrayList<>(this.players);
        java.util.Collections.shuffle(shuffledPlayers);
        int idCounter = 1;
        for (WebSocketSession session : shuffledPlayers) {
            playerIds.put(session, idCounter);
            System.out.println("[ROOM] Игроку " + session.getId() + " выпал случайный ID: " + idCounter);
            idCounter++;
        }
    }

    public Collection<Integer> getPlayerIdsList() {
        return playerIds.values();
    }

    public int getPlayerId(WebSocketSession session) {
        return playerIds.getOrDefault(session, -1);
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public void broadcast(Map<String, Object> messageMap) {
        try {
            String json = objectMapper.writeValueAsString(messageMap);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession player : players) {
                if (player.isOpen()) {
                    player.sendMessage(textMessage);
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка Broadcast в комнате " + roomId);
        }
    }

    public void broadcastExcept(WebSocketSession excludeSession, Map<String, Object> messageMap) {
        try {
            String json = objectMapper.writeValueAsString(messageMap);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession player : players) {
                if (player.isOpen() && !player.getId().equals(excludeSession.getId())) {
                    player.sendMessage(textMessage);
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка Emit в комнате " + roomId);
        }
    }

    public void initTurnOrder() {
        turnOrder.clear();
        Set<Integer> uniqueIds = new HashSet<>();
        for (WebSocketSession session : players) {
            int id = getPlayerId(session);
            if (id != -1 && !uniqueIds.contains(id)) {
                uniqueIds.add(id);
                turnOrder.add(id);
            }
        }
        Collections.sort(turnOrder);
        currentTurnIndex = 0;
        System.out.println("[ROOM] Очередь установлена: " + turnOrder);
    }

    public int getCurrentTurnPlayerId() {
        if (turnOrder.isEmpty()) return -1;
        return turnOrder.get(currentTurnIndex);
    }

    public List<Integer> getTurnOrder() {
        return new ArrayList<>(turnOrder);
    }

    public void removePlayerFromTurnOrder(int playerId) {
        Integer idToRemove = null;
        for (Integer id : turnOrder) {
            if (id == playerId) {
                idToRemove = id;
                break;
            }
        }
        if (idToRemove != null) {
            turnOrder.remove(idToRemove);
            System.out.println("[ROOM] Игрок " + playerId + " удалён из очереди ходов. Новая очередь: " + turnOrder);
            if (currentTurnIndex >= turnOrder.size() && !turnOrder.isEmpty()) {
                currentTurnIndex = 0;
            }
        }
    }

    public void resetToWaitingForPlayers() {
        playerTokens.clear();
        availableTokens.clear();
        availableTokens.addAll(Arrays.asList("SUN", "TREE", "KEY", "CROWN", "WOLF", "BIRD"));
        gamePhase = "WAITING_FOR_PLAYERS";
        turnOrder.clear();
        currentTurnIndex = 0;
        System.out.println("[ROOM] Комната " + roomId + " сброшена в состояние WAITING_FOR_PLAYERS");
    }

    public boolean isGameInProgress() {
        return "WAITING_FOR_TOKENS".equals(gamePhase) || "GAME_ACTIVE".equals(gamePhase) || "MINIGAME_ACTIVE".equals(gamePhase);
    }

    public void finishMiniGameAndShiftTurn(int winnerId) {
        System.out.println("[ROOM] Завершение мини-игры. Победитель: " + winnerId);

        // 1. Сохраняем захват клетки в boardState
        if (winnerId > 0 && pendingRow != -1 && pendingCol != -1) {
            setCell(pendingRow, pendingCol, winnerId);
            System.out.println("[ROOM] Клетка [" + pendingRow + "," + pendingCol + "] захвачена игроком " + winnerId);
        }

        // 2. Смена хода
        if (turnOrder != null && !turnOrder.isEmpty()) {
            currentTurnIndex = (currentTurnIndex + 1) % turnOrder.size();
        } else {
            initTurnOrder();
            currentTurnIndex = 0;
        }
        int nextPlayerId = getCurrentTurnPlayerId();
        updateLastActionTime(nextPlayerId);

        // 3. Очистка временных данных мини-игры
        this.currentGame = null;
        this.pendingRow = -1;
        this.pendingCol = -1;
        this.pendingAttackerId = -1;

        // 4. Отправка TURN_UPDATE
        Map<String, Object> turnData = new HashMap<>();
        turnData.put("activePlayerId", nextPlayerId);
        turnData.put("turnTimeoutSeconds", 180);
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "TURN_UPDATE");
        this.gamePhase = "GAME_ACTIVE";
        msg.put("data", turnData);
        broadcast(msg);

        System.out.println("[ROOM] ХОД ПЕРЕДАН. Сейчас должен ходить ID: " + nextPlayerId);
    }

    public void updateLastActionTime(int playerId) {
        lastActionTime.put(playerId, System.currentTimeMillis());
    }

    public boolean isPlayerTimedOut(int playerId, long timeoutMs) {
        Long lastTime = lastActionTime.get(playerId);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) > timeoutMs;
    }

    public void removePlayerFromTimeoutTracking(int playerId) {
        lastActionTime.remove(playerId);
    }
    public synchronized String deselectToken(int playerId) {
        String removed = playerTokens.remove(playerId);
        if (removed != null) {
            availableTokens.add(removed);
            System.out.println("[ROOM] Игрок " + playerId + " отменил выбор фишки " + removed);
        }
        return removed;
    }
    public Map<WebSocketSession, Integer> getPlayerIds() {
        return playerIds;
    }
    public void initBoardState() {
        boardState = new int[boardSize][boardSize];
    }

    public int[][] getBoardState() {
        return boardState;
    }

    public void setCell(int row, int col, int playerId) {
        boardState[row][col] = playerId;
    }
    public void markPlayerDisconnected(int playerId) {
        disconnectedSince.put(playerId, System.currentTimeMillis());
        System.out.println("[ROOM] Игрок " + playerId + " отключён. Даётся " + (DISCONNECT_TIMEOUT_MS/1000) + " сек на переподключение");
    }

    public boolean isPlayerDisconnected(int playerId) {
        Long time = disconnectedSince.get(playerId);
        if (time == null) return false;
        return (System.currentTimeMillis() - time) < DISCONNECT_TIMEOUT_MS;
    }

    public boolean canReconnect(int playerId) {
        Long time = disconnectedSince.get(playerId);
        if (time == null) return false;
        return (System.currentTimeMillis() - time) < DISCONNECT_TIMEOUT_MS;
    }

    public void removeDisconnectedPlayer(int playerId) {
        disconnectedSince.remove(playerId);
        // Здесь реально удаляем игрока из комнаты (вызываем существующий removePlayer)
        // Но нужна ссылка на MatchmakingService, чтобы не дублировать логику.
        // Проще вернуть признак, что игрок должен быть удалён, и пусть MatchmakingService это сделает.
    }

    public Collection<Integer> getExpiredDisconnectedPlayers() {
        long now = System.currentTimeMillis();
        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : disconnectedSince.entrySet()) {
            if (now - entry.getValue() >= DISCONNECT_TIMEOUT_MS) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }
    public Map<Integer, Long> getDisconnectedSince() {
        return disconnectedSince;
    }
}
