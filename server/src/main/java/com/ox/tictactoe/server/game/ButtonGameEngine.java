package com.ox.tictactoe.server.game;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ButtonGameEngine implements MiniGameEngine {

    private long startTime;
    private long gameStartTime;
    private boolean finished = false;
    private int winnerId = -1;
    private float syncTimer = 0;
    private final Set<Integer> activePlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Integer> fouledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final long TIMEOUT_AFTER_ACTIVATION = 10000; // 10 секунд после активации
    private static final long TOTAL_GAME_TIMEOUT = 30000;       // 30 секунд общий таймаут
    private boolean warningSent = false;

    @Override
    public void start(GameRoom room) {
        long delay = (long) (6000 + Math.random() * 5000);
        this.startTime = System.currentTimeMillis() + delay;
        this.gameStartTime = System.currentTimeMillis();

        for (WebSocketSession session : room.getPlayers()) {
            int playerId = room.getPlayerId(session);
            if (playerId != -1) {
                activePlayers.add(playerId);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("delayMs", delay);
        data.put("gameType", "BUTTON");
        data.put("players", new ArrayList<>(activePlayers));

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.MINIGAME_START, data
        );
        room.broadcast(msg);

        System.out.println("ButtonGame: старт через " + delay + "ms. Активные: " + activePlayers);
    }

    @Override
    public void handleAction(int playerId, Map<String, Object> data, GameRoom room) {
        if (finished) return;
        if (!activePlayers.contains(playerId) && !fouledPlayers.contains(playerId)) return;
        if (fouledPlayers.contains(playerId)) return;

        Object actionObj = data.get(NetworkConfig.Keys.ACTION);
        String action = String.valueOf(actionObj);
        if (!action.contains("CLICK")) return;

        long now = System.currentTimeMillis();
        boolean isFoul = now < startTime;

        if (isFoul) {
            fouledPlayers.add(playerId);
            activePlayers.remove(playerId);
            System.out.println("Фальстарт! Игрок " + playerId + " выбыл. Осталось: " + activePlayers);
            sendFoulNotification(playerId, room);

            if (activePlayers.size() == 1) {
                winnerId = activePlayers.iterator().next();
                System.out.println("Автоматическая победа игрока " + winnerId + " (остался один).");
                finishGame(room);
            } else if (activePlayers.isEmpty()) {
                // Если все выбыли — победитель атакующий
                winnerId = room.getPendingAttackerId();
                if (winnerId == -1) winnerId = 0;
                System.out.println("Все выбыли. Победитель: " + winnerId);
                finishGame(room);
            }
        } else {
            winnerId = playerId;
            System.out.println("Честный клик! Победитель: " + winnerId);
            finishGame(room);
        }
    }

    @Override
    public void update(float deltaTime, GameRoom room) {
        if (finished) return;
        long now = System.currentTimeMillis();

        // Общий таймаут (защита от зависания)
        if (now - gameStartTime > TOTAL_GAME_TIMEOUT) {
            System.out.println("ButtonGame: Общий таймаут! Игра завершена принудительно.");
            winnerId = room.getPendingAttackerId();
            if (winnerId == -1) winnerId = 0;
            finishGame(room);
            return;
        }

        // Таймаут после активации кнопки
        if (now > startTime) {
            long timeSinceActivation = now - startTime;

            if (timeSinceActivation > TIMEOUT_AFTER_ACTIVATION - 3000 && !warningSent) {
                sendTimeWarning(room, 3);
                warningSent = true;
            }

            if (timeSinceActivation > TIMEOUT_AFTER_ACTIVATION) {
                System.out.println("ButtonGame: Таймаут после активации (" + TIMEOUT_AFTER_ACTIVATION/1000 + " сек)");
                winnerId = room.getPendingAttackerId();
                if (winnerId == -1) winnerId = 0;
                finishGame(room);
            }

            // ========== ДОБАВЛЯЕМ РАССЫЛКУ TIME_SYNC ==========
            float timeLeft = (TIMEOUT_AFTER_ACTIVATION - timeSinceActivation) / 1000f;
            if (timeLeft < 0) timeLeft = 0;
            syncTimer += deltaTime;
            if (syncTimer >= 1.0f) {
                syncTimer = 0;
                Map<String, Object> syncData = new HashMap<>();
                syncData.put("gameAction", "TIME_SYNC");
                syncData.put("timeLeft", timeLeft);
                room.broadcast(NetworkConfig.MessageBuilder.createMessage(
                    NetworkConfig.MessageType.MINIGAME_ACTION, syncData
                ));
            }
        }
    }
    @Override
    public void onPlayerDisconnected(int playerId, GameRoom room) {
        System.out.println("ButtonGame: игрок " + playerId + " отключился");
        activePlayers.remove(playerId);
        fouledPlayers.remove(playerId);
    }

    private void sendFoulNotification(int disqualifiedPlayerId, GameRoom room) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "PLAYER_DISQUALIFIED");
        data.put("playerId", disqualifiedPlayerId);
        data.put("disqualifiedPlayers", new ArrayList<>(fouledPlayers));
        data.put("activePlayers", new ArrayList<>(activePlayers));
        data.put("reason", "ФАЛЬСТАРТ");

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.MINIGAME_ACTION, data
        );
        room.broadcast(msg);
    }

    private void sendTimeWarning(GameRoom room, int secondsLeft) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "TIME_WARNING");
        data.put("secondsLeft", secondsLeft);
        data.put("message", "Поторопитесь! Осталось " + secondsLeft + " сек.");

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.MINIGAME_ACTION, data
        );
        room.broadcast(msg);
    }

    private void finishGame(GameRoom room) {
        if (finished) return;
        finished = true;

        Map<String, Object> data = new HashMap<>();
        data.put("minigameWinnerId", winnerId);
        data.put("gameType", "BUTTON");
        data.put("row", room.getPendingRow());
        data.put("col", room.getPendingCol());

        if (!fouledPlayers.isEmpty()) {
            data.put("foulPlayers", new ArrayList<>(fouledPlayers));
        }

        Map<String, Object> msg = NetworkConfig.MessageBuilder.createMessage(
            NetworkConfig.MessageType.MINIGAME_RESULT, data
        );
        room.broadcast(msg);

        room.finishMiniGameAndShiftTurn(winnerId);
    }
    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put("finished", finished);
        if (finished) {
            state.put("winnerId", winnerId);
        } else {
            long now = System.currentTimeMillis();
            if (now >= startTime) {
                state.put("isActive", true);
                long timeSinceActivation = now - startTime;
                long timeLeft = TIMEOUT_AFTER_ACTIVATION - timeSinceActivation;
                if (timeLeft < 0) timeLeft = 0;
                state.put("timeRemaining", timeLeft / 1000.0); // секунды
            } else {
                state.put("isActive", false);
                long timeToActivation = startTime - now;
                state.put("timeRemaining", timeToActivation / 1000.0);
            }
            state.put("fouledPlayers", new ArrayList<>(fouledPlayers));
            state.put("activePlayers", new ArrayList<>(activePlayers));
        }
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

        boolean isFouled = fouledPlayers.contains(playerId);
        state.put("isFouled", isFouled);
        boolean isActive = activePlayers.contains(playerId);
        state.put("isActive", isActive);

        long now = System.currentTimeMillis();
        if (isActive) {
            if (now >= startTime) {
                long timeSinceActivation = now - startTime;
                long timeLeft = TIMEOUT_AFTER_ACTIVATION - timeSinceActivation;
                if (timeLeft < 0) timeLeft = 0;
                state.put("timeRemaining", timeLeft / 1000.0);
                state.put("isReady", true);
            } else {
                long timeToActivation = startTime - now;
                state.put("timeRemaining", timeToActivation / 1000.0);
                state.put("isReady", false);
            }
        } else {
            // Игрок выбыл или ещё не активен
            state.put("isReady", false);
            state.put("timeRemaining", 0);
        }
        return state;
    }

    @Override
    public boolean isFinished() { return finished; }
    @Override
    public String getGameType() { return "BUTTON"; }
}
