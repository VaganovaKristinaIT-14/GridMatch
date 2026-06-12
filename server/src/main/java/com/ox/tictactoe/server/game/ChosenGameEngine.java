package com.ox.tictactoe.server.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

public class ChosenGameEngine implements MiniGameEngine {
    private final int count = 15;
    private final int separateCount = count / 3;

    private final int maxNumberValue = 100;
    private final int maxAddValue = 100;
    private final int maxMultiValue = 20;

    private List<List<Integer>> numbers;
    private List<List<Integer>> addictions;
    private List<List<Integer>> multiplactions;

    private final float MAX_TIME_SECONDS = 45f;
    private long gameStartTime;
    private float syncTimer = 0;
    private boolean finished;
    private boolean resultSent;
    private int winnerId = -1;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Состояние прогресса для каждого игрока
    private static class PlayerState {
        int score = 0;
        int currentExampleNumber = 1;
        boolean isDone = false;
        // Время в миллисекундах от старта до момента ПОСЛЕДНЕГО ответа (любого)
        long lastActionTimeMs = 0;
    }

    private Map<Integer, PlayerState> playerStates;

    @Override
    public void start(GameRoom room) {
        // 1. Генерация (ваша логика)
        numbers = new ArrayList<>();
        addictions = new ArrayList<>();
        multiplactions = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < separateCount; i++) {
            numbers.add(List.of(random.nextInt(maxNumberValue), random.nextInt(maxNumberValue)));
            addictions.add(List.of(random.nextInt(maxAddValue), random.nextInt(maxAddValue),
                random.nextInt(maxAddValue), random.nextInt(maxAddValue)));
            multiplactions.add(List.of(random.nextInt(maxMultiValue), random.nextInt(maxMultiValue),
                random.nextInt(maxMultiValue), random.nextInt(maxMultiValue)));
        }

        playerStates = new HashMap<>();
        finished = false;
        resultSent = false;
        gameStartTime = System.currentTimeMillis();

        // 2. Отправляем команду клиентам открыть экран игры
        Map<String, Object> startScreenData = new HashMap<>();
        startScreenData.put(NetworkConfig.Keys.GAME_TYPE, "CHOSEN");
        startScreenData.put(NetworkConfig.Keys.ROW, room.getPendingRow());
        startScreenData.put(NetworkConfig.Keys.COL, room.getPendingCol());
        room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_START, startScreenData));

        // 3. Рассылаем каждому игроку ПЕРСОНАЛЬНО его первый вопрос
        for (WebSocketSession session : room.getPlayers()) {
            int playerId = room.getPlayerId(session);
            playerStates.put(playerId, new PlayerState());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("gameAction", "START");
            actionData.put("time", MAX_TIME_SECONDS);
            actionData.put("totalQuestions", count);
            appendQuestionData(actionData, 1);

            sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, actionData));
        }
    }

    @Override
    public void handleAction(int playerId, Map<String, Object> data, GameRoom room) {
        if (finished) return;

        PlayerState state = playerStates.get(playerId);
        if (state == null || state.isDone) return;

        // 1. ФИКСИРУЕМ ВРЕМЯ ДЕЙСТВИЯ (даже если ответ неверный)
        // Это позволит определить, кто был быстрее при одинаковом счете
        state.lastActionTimeMs = System.currentTimeMillis() - gameStartTime;

        // В MINIGAME_ACTION мы вложили data, достаем оттуда action ("LEFT" или "RIGHT")
        String actionType = (String) data.get("action");
        int qNum = state.currentExampleNumber;

        int leftVal = 0, rightVal = 0;

        if (qNum <= separateCount) {
            leftVal = numbers.get(qNum - 1).get(0);
            rightVal = numbers.get(qNum - 1).get(1);
        } else if (qNum <= separateCount * 2) {
            List<Integer> q = addictions.get(qNum - 1 - separateCount);
            leftVal = q.get(0) + q.get(1);
            rightVal = q.get(2) + q.get(3);
        } else {
            List<Integer> q = multiplactions.get(qNum - 1 - separateCount * 2);
            leftVal = q.get(0) * q.get(1);
            rightVal = q.get(2) * q.get(3);
        }

        boolean leftIsBigger = leftVal > rightVal;
        boolean isCorrect = (leftVal == rightVal) ||
            ("LEFT".equals(actionType) && leftIsBigger) ||
            ("RIGHT".equals(actionType) && !leftIsBigger);

        if (isCorrect) state.score++;
        state.currentExampleNumber++;

        if (state.currentExampleNumber > count) {
            state.isDone = true;
        }

        checkGameOver(room);

        // Если игра еще идет, шлем следующий вопрос
        if (!finished && !state.isDone) {
            Map<String, Object> nextData = new HashMap<>();
            nextData.put("gameAction", "NEXT_QUESTION");
            nextData.put("score", state.score);

            // НОВОЕ: Передаем актуальное время сервера, чтобы клиент синхронизировался
            long elapsed = System.currentTimeMillis() - gameStartTime;
            float actualTimeLeft = MAX_TIME_SECONDS - (elapsed / 1000f);
            nextData.put("timeLeft", actualTimeLeft);

            appendQuestionData(nextData, state.currentExampleNumber);

            WebSocketSession session = getSessionById(room, playerId);
            if (session != null) {
                sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, nextData));
            }
        } else if (!finished && state.isDone) {
            Map<String, Object> waitData = new HashMap<>();
            waitData.put("gameAction", "WAITING_FOR_OTHERS");
            waitData.put("score", state.score);
            WebSocketSession session = getSessionById(room, playerId);
            if (session != null) {
                sendToPlayer(session, NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, waitData));
            }
        }
    }

    @Override
    public void update(float deltaTime, GameRoom room) {
        if (finished) return;

        // Рассчитываем прошедшее время
        long elapsedTimeMs = System.currentTimeMillis() - gameStartTime;
        float actualTimeLeft = MAX_TIME_SECONDS - (elapsedTimeMs / 1000f);

        // 1. Проверка завершения по времени
        if (actualTimeLeft <= 0) {
            System.out.println("[CHOSEN SERVER] Время вышло!");
            for (PlayerState state : playerStates.values()) {
                state.isDone = true;
                if (state.lastActionTimeMs == 0) state.lastActionTimeMs = (long)(MAX_TIME_SECONDS * 1000);
            }
            checkGameOver(room);
            return;
        }

        // 2. СИНХРОНИЗАЦИЯ ТАЙМЕРА (Раз в секунду)
        syncTimer += deltaTime;
        if (syncTimer >= 1.0f) {
            syncTimer = 0;

            Map<String, Object> syncData = new HashMap<>();
            syncData.put("gameAction", "TIME_SYNC");
            syncData.put("timeLeft", actualTimeLeft);

            // Рассылаем всем игрокам в комнате
            room.broadcast(NetworkConfig.MessageBuilder.createMessage(
                NetworkConfig.MessageType.MINIGAME_ACTION, syncData
            ));
        }
    }
    @Override
    public void onPlayerDisconnected(int playerId, GameRoom room) {
        System.out.println("ChosenGame: игрок " + playerId + " отключился");

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
        state.put("gameType", "CHOSEN");
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
        state.put("currentQuestionNumber", ps.currentExampleNumber);
        state.put("isDone", ps.isDone);

        long elapsed = System.currentTimeMillis() - gameStartTime;
        float timeLeft = MAX_TIME_SECONDS - (elapsed / 1000f);
        if (timeLeft < 0) timeLeft = 0;
        state.put("timeLeft", timeLeft);
        state.put("totalQuestions", count);
        state.put("separateCount", separateCount);

        // Текущие выражения
        int qNum = ps.currentExampleNumber;
        if (qNum <= separateCount) {
            state.put("leftText", String.valueOf(numbers.get(qNum - 1).get(0)));
            state.put("rightText", String.valueOf(numbers.get(qNum - 1).get(1)));
        } else if (qNum <= separateCount * 2) {
            List<Integer> q = addictions.get(qNum - 1 - separateCount);
            state.put("leftText", q.get(0) + " + " + q.get(1));
            state.put("rightText", q.get(2) + " + " + q.get(3));
        } else {
            List<Integer> q = multiplactions.get(qNum - 1 - separateCount * 2);
            state.put("leftText", q.get(0) + " * " + q.get(1));
            state.put("rightText", q.get(2) + " * " + q.get(3));
        }

        return state;
    }

    private void checkGameOver(GameRoom room) {
        if (resultSent) return;

        boolean allDone = playerStates.values().stream().allMatch(s -> s.isDone);

        if (allDone) {
            finished = true;
            resultSent = true;

            int winnerId = -1;
            int maxScore = -1;
            long bestTime = Long.MAX_VALUE;

            for (Map.Entry<Integer, PlayerState> entry : playerStates.entrySet()) {
                int pId = entry.getKey();
                PlayerState st = entry.getValue();

                // 1. У кого больше правильных ответов
                if (st.score > maxScore) {
                    maxScore = st.score;
                    bestTime = st.lastActionTimeMs;
                    winnerId = pId;
                }

                // 2. Если очки РАВНЫ (неважно, закончили оба или оба застряли на 10-м вопросе по таймауту)
                // Победит тот, кто совершил свое последнее действие РАНЬШЕ.
                else if (st.score == maxScore) {
                    if (st.lastActionTimeMs < bestTime) {
                        bestTime = st.lastActionTimeMs;
                        winnerId = pId;
                    }
                }
            }
            this.winnerId = winnerId;
            // 1. Уведомляем интерфейс, чтобы показать красивую надпись
            Map<String, Object> localData = new HashMap<>();
            localData.put("gameAction", "GAME_OVER");
            localData.put("winnerId", winnerId);
            room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_ACTION, localData));

            // 2. Официально говорим доске захватить клетку
            Map<String, Object> resultData = new HashMap<>();
            resultData.put(NetworkConfig.Keys.MINIGAME_WINNER_ID, winnerId);
            resultData.put(NetworkConfig.Keys.ROW, room.getPendingRow());
            resultData.put(NetworkConfig.Keys.COL, room.getPendingCol());
            room.broadcast(NetworkConfig.MessageBuilder.createMessage(NetworkConfig.MessageType.MINIGAME_RESULT, resultData));

            // 3. Вызываем ВАШ метод смены хода
            room.finishMiniGameAndShiftTurn(winnerId);
        }
    }

    private void appendQuestionData(Map<String, Object> msg, int qNum) {
        if (qNum <= separateCount) {
            msg.put("leftText", String.valueOf(numbers.get(qNum - 1).get(0)));
            msg.put("rightText", String.valueOf(numbers.get(qNum - 1).get(1)));
        } else if (qNum <= separateCount * 2) {
            List<Integer> q = addictions.get(qNum - 1 - separateCount);
            msg.put("leftText", q.get(0) + " + " + q.get(1));
            msg.put("rightText", q.get(2) + " + " + q.get(3));
        } else {
            List<Integer> q = multiplactions.get(qNum - 1 - separateCount * 2);
            msg.put("leftText", q.get(0) + " * " + q.get(1));
            msg.put("rightText", q.get(2) + " * " + q.get(3));
        }
        msg.put("questionNum", qNum);
    }

    private WebSocketSession getSessionById(GameRoom room, int playerId) {
        for (WebSocketSession s : room.getPlayers()) {
            if (room.getPlayerId(s) == playerId) return s;
        }
        return null;
    }

    private void sendToPlayer(WebSocketSession session, Map<String, Object> msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public boolean isFinished() { return finished; }
    @Override
    public String getGameType() { return "CHOSEN"; }
}
