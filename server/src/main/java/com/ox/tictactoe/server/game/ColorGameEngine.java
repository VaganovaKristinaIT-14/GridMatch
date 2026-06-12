package com.ox.tictactoe.server.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

public class ColorGameEngine implements MiniGameEngine {
    private final int TOTAL_QUESTIONS = 15;
    private final float MAX_TIME_SECONDS = 22f; // 22 секунды по вашей задумке

    // Доступные цвета
    private final String[] COLORS = {"RED", "BLUE", "GREEN", "DARK_BLUE", "VIOLET", "ORANGE"};
    private List<String> questions;

    private long gameStartTime;
    private boolean finished;
    private boolean resultSent;
    private float syncTimer = 0;
    private int winnerId = -1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class PlayerState {
        int score = 0;
        int currentQuestionNumber = 1;
        boolean isDone = false;
        long lastActionTimeMs = 0; // Для победы при равном счете
    }

    private Map<Integer, PlayerState> playerStates;

    @Override
    public void start(GameRoom room) {
        // 1. Генерация 15 случайных цветов для игры
        questions = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            questions.add(COLORS[random.nextInt(COLORS.length)]);
        }

        playerStates = new HashMap<>();
        finished = false;
        resultSent = false;
        gameStartTime = System.currentTimeMillis();

        // Открываем экран на клиентах
        Map<String, Object> startScreenData = new HashMap<>();
        startScreenData.put(NetworkConfig.Keys.GAME_TYPE, "COLOR_GAME");
        startScreenData.put(NetworkConfig.Keys.ROW, room.getPendingRow());
        startScreenData.put(NetworkConfig.Keys.COL, room.getPendingCol());
        room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_START, startScreenData));

        // Рассылаем игрокам первый цвет
        for (WebSocketSession session : room.getPlayers()) {
            int playerId = room.getPlayerId(session);
            playerStates.put(playerId, new PlayerState());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("gameAction", "START");
            actionData.put("time", MAX_TIME_SECONDS);
            actionData.put("totalQuestions", TOTAL_QUESTIONS);
            actionData.put("targetColor", questions.get(0));

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
            if (actionType == null) return;

            // Фиксируем время нажатия (тайм-брейк)
            state.lastActionTimeMs = System.currentTimeMillis() - gameStartTime;

            // Проверяем правильность: совпадает ли присланный цвет с загаданным на этом шаге
            String correctColor = questions.get(state.currentQuestionNumber - 1);
            if (actionType.equals(correctColor)) {
                state.score++;
            }

            state.currentQuestionNumber++;

            if (state.currentQuestionNumber > TOTAL_QUESTIONS) {
                state.isDone = true;
            }

            checkGameOver(room);

            // Шлем следующий вопрос
            if (!finished && !state.isDone) {
                Map<String, Object> nextData = new HashMap<>();
                nextData.put("gameAction", "NEXT_QUESTION");
                nextData.put("score", state.score);
                nextData.put("targetColor", questions.get(state.currentQuestionNumber - 1));

                WebSocketSession session = getSessionById(room, playerId);
                if (session != null) {
                    sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, nextData));
                }
            } else if (!finished && state.isDone) {
                // Игрок всё решил, просим подождать
                Map<String, Object> waitData = new HashMap<>();
                waitData.put("gameAction", "WAITING_FOR_OTHERS");
                waitData.put("score", state.score);
                WebSocketSession session = getSessionById(room, playerId);
                if (session != null) {
                    sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, waitData));
                }
            }
        } catch (Exception e) {
            System.err.println("[COLOR SERVER ERROR] " + e.getMessage());
        }
    }

    @Override
    public void update(float deltaTime, GameRoom room) {
        if (finished) return;

        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        float actualTimeLeft = MAX_TIME_SECONDS - (elapsedMs / 1000f);

        // Конец по времени
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

        // Синхронизация времени раз в секунду
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
        System.out.println("ColorGame: игрок " + playerId + " отключился");

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
        return Map.of();
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

        // Определяем победителя (кто больше набрал, при равенстве - кто быстрее нажимал)
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
        // Завершение
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
        state.put("currentQuestionNumber", ps.currentQuestionNumber);
        state.put("isDone", ps.isDone);

        long elapsed = System.currentTimeMillis() - gameStartTime;
        float timeLeft = MAX_TIME_SECONDS - (elapsed / 1000f);
        if (timeLeft < 0) timeLeft = 0;
        state.put("timeLeft", timeLeft);

        // Текущий целевой цвет (для отображения)
        if (ps.currentQuestionNumber <= questions.size()) {
            state.put("targetColor", questions.get(ps.currentQuestionNumber - 1));
        }
        state.put("totalQuestions", TOTAL_QUESTIONS);
        return state;
    }
    @Override public boolean isFinished() { return finished; }
    @Override public String getGameType() { return "COLOR_GAME"; }
}
