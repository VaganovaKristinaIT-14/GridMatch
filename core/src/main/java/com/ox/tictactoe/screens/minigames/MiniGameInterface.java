package com.ox.tictactoe.screens.minigames;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.JsonValue;

public interface MiniGameInterface {
    // ЗАЧЕМ: Передать данные об игроках из BoardScreen внутрь мини-игры
    void setup(int myId, com.badlogic.gdx.utils.Array<com.ox.tictactoe.PlayerModel> players);
    void init();               // Загрузка ресурсов конкретной игры
    void update(float delta);  // Логика (таймеры, проверка победы)
    void draw(SpriteBatch batch); // Отрисовка
    void handleInput();        // Касания экрана
    void handleNetworkMessage(JsonValue json); // Реакция на ходы врага из сети
    boolean isFinished();      // Сигнал главному экрану, что игра окончена
    void dispose();            // Очистка памяти
    void restoreState(JsonValue state);
}
