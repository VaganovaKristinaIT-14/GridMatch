package com.ox.tictactoe.screens.minigames;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;
import com.ox.tictactoe.PlayerModel;
import com.ox.tictactoe.screens.BoardResultOverlay;
import com.ox.tictactoe.screens.BoardScreen;
import com.ox.tictactoe.screens.GameResultOverlay;

import java.util.HashMap;
import java.util.Map;

/**
 * Мини-игра кнопка
 */

public class ButtonGame extends ScreenAdapter implements MiniGameInterface {
    private final MainGame game;
    private final BoardScreen boardScreen;
    private final int col, row;

    private OrthographicCamera camera;
    private Viewport viewport;
    private GlyphLayout glyphLayout; // для отрисовки текста таймера

    private TextureRegion background, buttonActive, buttonInactive, buttonPressed;

    private long timer;
    private boolean isReady, gameFinished, myClickSent;
    private int winner = 0;
    private int myId;
    private boolean iAmFailed = false;

    private GameResultOverlay resultOverlay;

    // Для таймера
    private float timeLeft = 0f;
    private boolean timerActive = false;

    public ButtonGame(MainGame game, BoardScreen boardScreen, int col, int row) {
        this.game = game;
        this.boardScreen = boardScreen;
        this.col = col;
        this.row = row;
        camera = new OrthographicCamera();
        viewport = new FitViewport(1080, 1920, camera);
        camera.position.set(1080 / 2f, 1920 / 2f, 0);
        glyphLayout = new GlyphLayout();
    }

    @Override
    public void setup(int myId, Array<PlayerModel> players) {
        this.myId = myId;
        Gdx.app.log("ButtonGame", "Установлен мой ID: " + myId);
        // Если захочешь хранить список игроков в кнопке, можно добавить поле:
        // this.players = players;
    }

    @Override
    public void init() {
        background = game.allBackTexture;
        buttonActive = game.atlas.findRegion("buttongame_button_ready");
        buttonInactive = game.atlas.findRegion("buttongame_button_disabled");
        buttonPressed = game.atlas.findRegion("buttongame_button_pressed");

        isReady = false;
        gameFinished = false;
        myClickSent = false;
        iAmFailed = false;
        timerActive = false;
        timeLeft = 0f;
        resultOverlay = new GameResultOverlay(game);
    }

    @Override
    public void render(float delta) {
        // Чистим экран
        com.badlogic.gdx.utils.ScreenUtils.clear(0, 0, 0, 1);

        viewport.apply();
        game.batch.setProjectionMatrix(camera.combined);

        // Логика
        if (!gameFinished) {
            handleInput();
            update(delta);
        } else {
            resultOverlay.update(delta);
        }

        // Отрисовка
        game.batch.begin();

        // Рисуем кнопку (если игра идет)
        draw(game.batch);

        // Если игра закончилась — оверлей нарисует "all" поверх кнопки и плашку
        if (gameFinished && resultOverlay != null) {
            resultOverlay.draw(game.batch);
        }

        game.batch.end();
    }

    public void draw(SpriteBatch batch) {
        if (background != null) batch.draw(background, 0, 0, 1080, 1920);

        TextureRegion currentFrame;
        if (myClickSent || gameFinished) {
            currentFrame = buttonPressed;  // Кнопка нажата
        } else if (isReady) {
            currentFrame = buttonActive;   // Кнопка активна (зеленая)
        } else {
            currentFrame = buttonInactive; // Кнопка неактивна (серая)
        }

        if (currentFrame != null) {
            batch.draw(currentFrame, (1080-600)/2f, (1920-600)/2f, 600, 600);
        }

        // Отрисовка таймера (только если активен, игра не окончена, кнопка готова и время > 0)
        if (timerActive && !gameFinished && !myClickSent && isReady && timeLeft > 0) {
            if (game.mainFont != null) {
                game.mainFont.setColor(Color.YELLOW);
                game.mainFont.getData().setScale(1.5f);
                String timeStr = String.format("%.1f", timeLeft);
                glyphLayout.setText(game.mainFont, timeStr);
                float x = (1080 - glyphLayout.width) / 2f;
                float y = 1400f; // над кнопкой
                game.mainFont.draw(batch, timeStr, x, y);
            }
        }
    }

    @Override
    public void handleInput() {
        if (game.isInputBlocked()) return;
        if (gameFinished || myClickSent || iAmFailed) return;

        if (Gdx.input.justTouched()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "MINIGAME_ACTION");
            Map<String, Object> data = new HashMap<>();
            data.put("action", "CLICK");
            message.put("data", data);

            if (game.gameClient != null) {
                game.gameClient.sendJson(message);
                myClickSent = true;
                Gdx.app.log("ButtonGame", "Пакет отправлен!");
            }
        }
    }

    @Override
    public void handleNetworkMessage(JsonValue json) {
        Gdx.app.log("NETWORK_DEBUG", "Пришло: " + json.toString());

        String type = json.getString("type");
        JsonValue data = json.get("data");

        if ("MINIGAME_START".equals(type)) {
            try {
                // Сервер теперь шлёт delayMs (число, а не строку)
                long delayMs = data.getLong("delayMs", 6000L);
                this.timer = System.currentTimeMillis() + delayMs;
                Gdx.app.log("ButtonGame", "Таймер установлен через " + delayMs + " мс, активация в " + this.timer);
            } catch (Exception e) {
                Gdx.app.error("ButtonGame", "Критическая ошибка парсинга: " + e.getMessage());
                // fallback – используем дефолтную задержку 6 секунд
                this.timer = System.currentTimeMillis() + 6000;
            }
        }

        // Обработка TIME_SYNC (приходит в MINIGAME_ACTION)
        if ("MINIGAME_ACTION".equals(type)) {
            String gameAction = data.getString("gameAction", "");
            if ("TIME_SYNC".equals(gameAction)) {
                timeLeft = data.getFloat("timeLeft");
                timerActive = true;
                Gdx.app.log("ButtonGame", "TIME_SYNC: " + timeLeft);
            }
        }

        if ("MINIGAME_RESULT".equals(type)) {
            // 1. Берем данные из JSON, который прислал сервер
            int serverWinner = data.getInt("minigameWinnerId", 0);
            int serverRow = data.getInt("row", -1);
            int serverCol = data.getInt("col", -1);

            this.winner = serverWinner;
            this.gameFinished = true;
            this.timerActive = false; // останавливаем таймер

            Gdx.app.log("ButtonGame", "Результат от сервера: Winner=" + serverWinner + " Cell=[" + serverRow + "," + serverCol + "]");

            if (resultOverlay != null) {
                resultOverlay.show(winner, game.myResourceId, false, boardScreen);
            }

            // 2. Вызываем доску, используя координаты ОТ СЕРВЕРА
            if (boardScreen != null && serverRow != -1 && serverCol != -1) {
                // Убедись, что в BoardScreen порядок: row, col, winner
                boardScreen.onCellCaptured(serverRow, serverCol, winner);
            }
        }
        if (type.equals("GAME_OVER")) {
            int winnerId = data.getInt("winnerId", 0);
            gameFinished = true;

            // Показываем оверлей основной игры на доске
            if (boardScreen != null) {
                if (winnerId == myId) {
                    boardScreen.showGameOver(BoardResultOverlay.ResultState.WIN);
                } else {
                    boardScreen.showGameOver(BoardResultOverlay.ResultState.LOSE);
                }
                game.setScreen(boardScreen);
            }
            return;
        }
    }

    @Override
    public void update(float delta) {
        if (gameFinished) return;

        if (!isReady && timer > 0) {
            long now = System.currentTimeMillis();
            // Если время уже наступило (даже если мы были в LoadingScreen)
            if (now >= timer) {
                isReady = true;
                Gdx.app.log("ButtonGame", "КНОПКА АКТИВИРОВАНА");
            }
        }

        // Обновляем локальный таймер для плавного убывания
        if (timerActive && !gameFinished && !myClickSent && isReady && timeLeft > 0) {
            timeLeft -= delta;
            if (timeLeft < 0) timeLeft = 0;
        }
    }
    @Override
    public void restoreState(JsonValue state) {
        boolean finished = state.getBoolean("finished", false);
        if (finished) {
            this.gameFinished = true;
            this.winner = state.getInt("winnerId", 0);
            if (resultOverlay != null) {
                resultOverlay.show(winner, game.myResourceId, false, boardScreen);
            }
            return;
        }

        boolean isFouled = state.getBoolean("isFouled", false);
        if (isFouled) {
            this.iAmFailed = true;
            this.myClickSent = true; // блокируем возможность клика
            return;
        }

        boolean isActive = state.getBoolean("isActive", false);
        boolean isReady = state.getBoolean("isReady", false);
        double timeRemaining = state.getDouble("timeRemaining", 0.0);

        if (isActive && isReady) {
            this.isReady = true;
            this.timeLeft = (float) timeRemaining;
            this.timerActive = true;
        } else if (isActive && !isReady) {
            // Ещё не активирована, но игрок в списке активных – ждём старта
            this.isReady = false;
            long remainingMillis = (long) (timeRemaining * 1000);
            this.timer = System.currentTimeMillis() + remainingMillis;
        } else {
            // Игрок не активен (возможно, выбыл или ещё не добавлен)
            this.isReady = false;
            this.myClickSent = true; // блокируем
        }
    }
    @Override public void show() { init(); }
    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public boolean isFinished() { return gameFinished; }
    @Override public void dispose() {}
}
