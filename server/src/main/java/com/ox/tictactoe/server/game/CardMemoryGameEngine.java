package com.ox.tictactoe.server.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

public class CardMemoryGameEngine implements MiniGameEngine {
    private final int TOTAL_ROUNDS = 3;
    private final float MAX_TIME_SECONDS = 45f; // Даем 45 секунд, так как нужно запоминать

    private final String[] CARDS = {"LUNE", "SUN", "STAR", "CLOUD"};
    private List<List<String>> targetSequences;

    private long gameStartTime;
    private boolean finished;
    private boolean resultSent;
    private float syncTimer = 0;
    private int winnerId = -1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class PlayerState {
        int score = 0;
        int currentRound = 0; // 0, 1, 2
        boolean isDone = false;
        long lastActionTimeMs = 0;
    }

    private Map<Integer, PlayerState> playerStates;

    @Override
    public void start(GameRoom room) {
        // Генерация последовательностей для 3 раундов (длина: 3, 4, 5)
        targetSequences = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < TOTAL_ROUNDS; i++) {
            List<String> seq = new ArrayList<>();
            int seqLength = 3 + i; // Раунд 0 -> 3 карты, Раунд 1 -> 4 карты, Раунд 2 -> 5 карт
            for (int j = 0; j < seqLength; j++) {
                seq.add(CARDS[random.nextInt(CARDS.length)]);
            }
            targetSequences.add(seq);
        }

        playerStates = new HashMap<>();
        finished = false;
        resultSent = false;
        gameStartTime = System.currentTimeMillis();

        // Открываем экран на клиентах
        Map<String, Object> startScreenData = new HashMap<>();
        startScreenData.put(NetworkConfig.Keys.GAME_TYPE, "CARD_MEMORY");
        startScreenData.put(NetworkConfig.Keys.ROW, room.getPendingRow());
        startScreenData.put(NetworkConfig.Keys.COL, room.getPendingCol());
        room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_START, startScreenData));

        // Рассылаем первую последовательность
        for (WebSocketSession session : room.getPlayers()) {
            int playerId = room.getPlayerId(session);
            playerStates.put(playerId, new PlayerState());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("gameAction", "START");
            actionData.put("time", MAX_TIME_SECONDS);
            actionData.put("totalRounds", TOTAL_ROUNDS);
            actionData.put("targetSequence", targetSequences.get(0));

            sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, actionData));
        }
    }

    @Override
    public void handleAction(int playerId, Map<String, Object> data, GameRoom room) {
        if (finished) return;

        try {
            PlayerState state = playerStates.get(playerId);
            if (state == null || state.isDone) return;

            String actionType = (String) data.get("action");
            if (!"SUBMIT".equals(actionType)) return;

            // Фиксируем время попытки
            state.lastActionTimeMs = System.currentTimeMillis() - gameStartTime;

            // Достаем массив, который прислал клиент
            List<String> inputSequence = (List<String>) data.get("sequence");
            List<String> correctSequence = targetSequences.get(state.currentRound);

            // Проверяем совпадение
            if (inputSequence != null && inputSequence.equals(correctSequence)) {
                state.score++; // Нажал всё правильно!
            }

            state.currentRound++;

            // Если прошел все 3 раунда
            if (state.currentRound >= TOTAL_ROUNDS) {
                state.isDone = true;
            }

            checkGameOver(room);

            // Если игра продолжается - шлем следующий раунд
            if (!finished && !state.isDone) {
                Map<String, Object> nextData = new HashMap<>();
                nextData.put("gameAction", "NEXT_ROUND");
                nextData.put("score", state.score);
                nextData.put("targetSequence", targetSequences.get(state.currentRound));

                WebSocketSession session = getSessionById(room, playerId);
                if (session != null) {
                    sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, nextData));
                }
            } else if (!finished && state.isDone) {
                // НОВОВВЕДЕНИЕ: Игрок всё решил, просим подождать других
                Map<String, Object> waitData = new HashMap<>();
                waitData.put("gameAction", "WAITING_FOR_OTHERS");
                waitData.put("score", state.score);
                WebSocketSession session = getSessionById(room, playerId);
                if (session != null) sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, waitData));
            }
        } catch (Exception e) {
            System.err.println("[CARD SERVER ERROR] " + e.getMessage());
        }
    }

    @Override
    public void update(float deltaTime, GameRoom room) {
        if (finished) return;

        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        float actualTimeLeft = MAX_TIME_SECONDS - (elapsedMs / 1000f);

        if (actualTimeLeft <= 0) {
            for (PlayerState state : playerStates.values()) {
                if (!state.isDone) {
                    state.isDone = true;
                    if (state.lastActionTimeMs == 0) state.lastActionTimeMs = (long)(MAX_TIME_SECONDS * 1000);
                }
            }
            checkGameOver(room);
            return;
        }

        syncTimer += deltaTime;
        if (syncTimer >= 1.0f) {
            syncTimer = 0;
            Map<String, Object> syncData = new HashMap<>();
            syncData.put("gameAction", "TIME_SYNC");
            syncData.put("timeLeft", actualTimeLeft);
            room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, syncData));
        }
    }
    @Override
    public void onPlayerDisconnected(int playerId, GameRoom room) {
        System.out.println("CardMemoryGame: игрок " + playerId + " отключился");

        PlayerState state = playerStates.get(playerId);
        if (state != null && !state.isDone) {
            state.isDone = true;
            if (state.lastActionTimeMs == 0) {
                state.lastActionTimeMs = System.currentTimeMillis() - gameStartTime;
            }
            checkGameOver(room);
        }
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put("gameType", "CARD_MEMORY");
        state.put("finished", finished);
        if (finished) state.put("winnerId", winnerId);
        return state;
    }

    @Override
    public Map<String, Object> getPlayerState(int playerId) {
        Map<String, Object> state = new HashMap<>();
        if (finished) {
            state.put("finished", true);
            state.put("winnerId", winnerId);
            return state;
        }

        PlayerState ps = playerStates.get(playerId);
        if (ps == null) {
            state.put("finished", true);
            state.put("winnerId", -1);
            return state;
        }

        state.put("score", ps.score);
        state.put("currentRound", ps.currentRound);
        state.put("isDone", ps.isDone);

        long elapsed = System.currentTimeMillis() - gameStartTime;
        float timeLeft = MAX_TIME_SECONDS - (elapsed / 1000f);
        if (timeLeft < 0) timeLeft = 0;
        state.put("timeLeft", timeLeft);
        state.put("totalRounds", TOTAL_ROUNDS);

        // Текущая целевая последовательность (для отображения)
        if (ps.currentRound < targetSequences.size()) {
            state.put("targetSequence", targetSequences.get(ps.currentRound));
        }

        return state;
    }

    private void checkGameOver(GameRoom room) {
        if (resultSent) return;

        boolean allFinished = playerStates.values().stream().allMatch(s -> s.isDone);
        if (!allFinished) return;

        finished = true;
        resultSent = true;

        int winnerId = -1;
        int maxScore = -1;
        long bestTime = Long.MAX_VALUE;

        for (Map.Entry<Integer, PlayerState> entry : playerStates.entrySet()) {
            int pId = entry.getKey();
            PlayerState st = entry.getValue();

            if (st.score > maxScore) {
                maxScore = st.score;
                bestTime = st.lastActionTimeMs;
                winnerId = pId;
            } else if (st.score == maxScore) {
                if (st.lastActionTimeMs < bestTime) {
                    bestTime = st.lastActionTimeMs;
                    winnerId = pId;
                }
            }
        }
        this.winnerId = winnerId;
        Map<String, Object> localData = new HashMap<>();
        localData.put("gameAction", "GAME_OVER");
        localData.put("winnerId", winnerId);
        room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, localData));

        Map<String, Object> resultData = new HashMap<>();
        resultData.put(NetworkConfig.Keys.MINIGAME_WINNER_ID, winnerId);
        resultData.put(NetworkConfig.Keys.ROW, room.getPendingRow());
        resultData.put(NetworkConfig.Keys.COL, room.getPendingCol());
        room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_RESULT, resultData));

        room.finishMiniGameAndShiftTurn(winnerId);
    }

    private WebSocketSession getSessionById(GameRoom room, int pId) { return room.getPlayers().stream().filter(s -> room.getPlayerId(s) == pId).findFirst().orElse(null); }
    private void sendToPlayer(WebSocketSession s, Map<String, Object> m) { try { if (s.isOpen()) s.sendMessage(new TextMessage(objectMapper.writeValueAsString(m))); } catch (Exception e) {} }

    @Override public boolean isFinished() { return finished; }
    @Override public String getGameType() { return "CARD_MEMORY"; }
}
