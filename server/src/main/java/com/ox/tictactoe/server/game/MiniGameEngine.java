package com.ox.tictactoe.server.game;

import com.ox.tictactoe.server.model.GameRoom;
import java.util.Map;

public interface MiniGameEngine {

    // 1. Запуск мини-игры (рассылает START, таймеры)
    void start(GameRoom room);

    // 2. Обработка действия игрока (клик, движение и т.д.)
    void handleAction(int playerId, Map<String, Object> data, GameRoom room);

    // 3. Проверка таймеров (фальстарт, время вышло)
    void update(float deltaTime, GameRoom room);

    // 4. Завершена ли игра?
    boolean isFinished();

    // 5. Тип игры (для логирования)
    String getGameType();
    default void onPlayerDisconnected(int playerId, GameRoom room) {
    }
    Map<String, Object> getState();
    Map<String, Object> getPlayerState(int playerId);
}
