package com.ox.tictactoe.screens.minigames;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
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

public class ColorGame extends ScreenAdapter implements MiniGameInterface {

    private final MainGame game;
    private final BoardScreen boardScreen;

    private OrthographicCamera camera;
    private Viewport viewport;

    private TextureRegion background, gameTitle;
    private TextureRegion redColor, blueColor, greenColor, darkBlueColor, violetColor, orangeColor;
    private GlyphLayout glyphLayout;

    private GameResultOverlay resultOverlay;

    private int myId = -1;
    private int winnerId = -1;

    private boolean gameFinished;
    private boolean waitingForNext;
    private boolean isWaitingForOthers = false;

    private float timeLeft = 22f;
    private int score = 0;
    private int totalQuestions = 15;

    // То, что загадал сервер
    private String targetColorKey = "";
    private String targetColorText = "Загрузка...";
    private Color targetFontColor = Color.WHITE;

    // Зоны клика
    private Rectangle rectRed, rectBlue, rectGreen, rectDarkBlue, rectViolet, rectOrange;

    public ColorGame(MainGame game, BoardScreen boardScreen, int col, int row) {
        this.game = game;
        this.boardScreen = boardScreen;

        camera = new OrthographicCamera();
        viewport = new FitViewport(1080, 1920, camera);
        camera.position.set(1080 / 2f, 1920 / 2f, 0);

        glyphLayout = new GlyphLayout();
        resultOverlay = new GameResultOverlay(game);

        init();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();

        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        draw(game.batch);
        game.batch.end();
    }

    @Override
    public void setup(int myId, Array<PlayerModel> players) {
        this.myId = myId;
    }

    @Override
    public void init() {
        background = game.allBackTexture;
        gameTitle = game.atlas.findRegion("colorgame_text");

        redColor = game.atlas.findRegion("colorgame_red");
        blueColor = game.atlas.findRegion("colorgame_blue"); // Это светло-голубой
        greenColor = game.atlas.findRegion("colorgame_green");
        darkBlueColor = game.atlas.findRegion("colorgame_darkblue"); // Это синий
        violetColor = game.atlas.findRegion("colorgame_violet");
        orangeColor= game.atlas.findRegion("colorgame_orange");

        gameFinished = false;
        waitingForNext = true;
        isWaitingForOthers = false;

        // РАСЧЕТ СЕТКИ КНОПОК 3х2 (как на картинке)
        float btnW = 257f;
        float btnH = 259f;
        float gap = 40f; // Расстояние между кнопками

        // Вычисляем ширину всех 3 кнопок + 2 зазоров между ними
        float totalWidth = (btnW * 3) + (gap * 2);
        float startX = (1080 - totalWidth) / 2f; // Идеальный центр по X

        float bottomY = 400f; // Высота нижней линии кнопок
        float topY = bottomY + btnH + gap; // Высота верхней линии кнопок

        // Верхний ряд (Красный, Голубой, Зелёный)
        rectRed = new Rectangle(startX, topY, btnW, btnH);
        rectBlue = new Rectangle(startX + btnW + gap, topY, btnW, btnH);
        rectGreen = new Rectangle(startX + (btnW + gap) * 2, topY, btnW, btnH);

        // Нижний ряд (Синий, Фиолетовый, Оранжевый)
        rectDarkBlue = new Rectangle(startX, bottomY, btnW, btnH);
        rectViolet = new Rectangle(startX + btnW + gap, bottomY, btnW, btnH);
        rectOrange = new Rectangle(startX + (btnW + gap) * 2, bottomY, btnW, btnH);
    }

    @Override
    public void update(float delta) {
        if (gameFinished) {
            resultOverlay.update(delta);
            return;
        }

        if (timeLeft > 0 && !waitingForNext) {
            timeLeft -= delta;
            if (timeLeft < 0) timeLeft = 0;
        }

        handleInput();
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (resultOverlay.isVisible()) {
            resultOverlay.draw(batch);
            return;
        }

        if (background != null) batch.draw(background, 0, 0, 1080, 1920);

        // Рисуем картинку заголовка "Выбери нужный цвет!"
        if (gameTitle != null) {
            float titleX = (1080 - gameTitle.getRegionWidth()) / 2f;
            batch.draw(gameTitle, titleX, 1300f);
        }

        // Рисуем квадратики
        if (redColor != null) batch.draw(redColor, rectRed.x, rectRed.y, rectRed.width, rectRed.height);
        if (blueColor != null) batch.draw(blueColor, rectBlue.x, rectBlue.y, rectBlue.width, rectBlue.height);
        if (greenColor != null) batch.draw(greenColor, rectGreen.x, rectGreen.y, rectGreen.width, rectGreen.height);
        if (darkBlueColor != null) batch.draw(darkBlueColor, rectDarkBlue.x, rectDarkBlue.y, rectDarkBlue.width, rectDarkBlue.height);
        if (violetColor != null) batch.draw(violetColor, rectViolet.x, rectViolet.y, rectViolet.width, rectViolet.height);
        if (orangeColor != null) batch.draw(orangeColor, rectOrange.x, rectOrange.y, rectOrange.width, rectOrange.height);

        // Отрисовка текста
        if (game.mainFont != null) {
            game.mainFont.getData().setScale(1.2f);
            game.mainFont.setColor(Color.RED);

            // Таймер и Счет
            String topString = "Время: " + (int)timeLeft + " сек      Счёт: " + score + " из " + totalQuestions;
            glyphLayout.setText(game.mainFont, topString);
            game.mainFont.draw(batch, topString, (1080 - glyphLayout.width) / 2f, 1700f);

            // ТЕКСТ ЦВЕТА ИЛИ ОЖИДАНИЯ
            if (isWaitingForOthers) {
                game.mainFont.setColor(Color.YELLOW);
                game.mainFont.getData().setScale(1.5f);
                glyphLayout.setText(game.mainFont, "Подождите других\nигроков...", Color.YELLOW, 950f, Align.center, true);
                game.mainFont.draw(batch, glyphLayout, (1080 - 950f) / 2f, 1150f);
            } else if (!waitingForNext) {
                game.mainFont.setColor(targetFontColor); // Красим текст в загаданный цвет!
                game.mainFont.getData().setScale(1.8f);  // Делаем его побольше
                glyphLayout.setText(game.mainFont, targetColorText);
                game.mainFont.draw(batch, targetColorText, (1080 - glyphLayout.width) / 2f, 1100f);
            }
        }
    }

    @Override
    public void handleInput() {
        if (game.isInputBlocked()) return;
        if (gameFinished || waitingForNext || !Gdx.input.justTouched()) return;

        Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touch);

        if (rectRed.contains(touch.x, touch.y)) sendAction("RED");
        else if (rectBlue.contains(touch.x, touch.y)) sendAction("BLUE");
        else if (rectGreen.contains(touch.x, touch.y)) sendAction("GREEN");
        else if (rectDarkBlue.contains(touch.x, touch.y)) sendAction("DARK_BLUE");
        else if (rectViolet.contains(touch.x, touch.y)) sendAction("VIOLET");
        else if (rectOrange.contains(touch.x, touch.y)) sendAction("ORANGE");
    }

    private void sendAction(String color) {
        waitingForNext = true;
        String json = String.format("{\"type\":\"MINIGAME_ACTION\", \"data\":{\"action\":\"%s\"}}", color);
        game.gameClient.send(json);
    }

    @Override
    public void handleNetworkMessage(JsonValue json) {
        String type = json.getString("type", "");

        if (type.equals("MINIGAME_ACTION")) {
            JsonValue data = json.get("data");
            if (data == null) return;

            String gameAction = data.getString("gameAction", "");

            try {
                if (gameAction.equals("TIME_SYNC")) {
                    timeLeft = data.getFloat("timeLeft");
                    return;
                }

                if (gameAction.equals("WAITING_FOR_OTHERS")) {
                    isWaitingForOthers = true;
                    if (data.has("score")) {
                        score = data.getInt("score");
                    }
                    return;
                }

                if (gameAction.equals("START") || gameAction.equals("NEXT_QUESTION")) {
                    isWaitingForOthers = false;
                    if (data.has("timeLeft")) timeLeft = data.getFloat("timeLeft");
                    else if (gameAction.equals("START")) timeLeft = data.getFloat("time", 22f);

                    if (data.has("score")) score = data.getInt("score");

                    // Обновляем загаданный цвет
                    targetColorKey = data.getString("targetColor", "RED");
                    updateTargetColorText(targetColorKey);

                    waitingForNext = false;
                }
                else if (gameAction.equals("GAME_OVER")) {
                    if (!gameFinished) {
                        gameFinished = true;
                        if (data.has("winnerId")) winnerId = data.getInt("winnerId");
                        resultOverlay.show(winnerId, myId, false, boardScreen);
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("COLOR_GAME", "Ошибка парсинга: " + e.getMessage());
                waitingForNext = false;
            }
        }

        if (type.equals("MINIGAME_RESULT")) {
            if (!gameFinished) {
                gameFinished = true;
                JsonValue data = json.get("data");
                if (data != null && data.has("minigameWinnerId")) winnerId = data.getInt("minigameWinnerId");
                resultOverlay.show(winnerId, myId, false, boardScreen);
            }
        }
        if (type.equals("GAME_OVER")) {
            JsonValue data = json.get("data");
            int winnerId = data.getInt("winnerId", 0);
            gameFinished = true;

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

    // Вспомогательный метод для красивого отображения названия цвета
    private void updateTargetColorText(String key) {
        switch (key) {
            case "RED":
                targetColorText = "КРАСНЫЙ";
                targetFontColor = Color.RED;
                break;
            case "BLUE":
                targetColorText = "ГОЛУБОЙ";
                targetFontColor = Color.CYAN;
                break;
            case "GREEN":
                targetColorText = "ЗЕЛЁНЫЙ";
                targetFontColor = Color.GREEN;
                break;
            case "DARK_BLUE":
                targetColorText = "СИНИЙ";
                targetFontColor = Color.BLUE;
                break;
            case "VIOLET":
                targetColorText = "ФИОЛЕТОВЫЙ";
                targetFontColor = Color.PURPLE;
                break;
            case "ORANGE":
                targetColorText = "ОРАНЖЕВЫЙ";
                targetFontColor = Color.ORANGE;
                break;
        }
    }
    @Override
    public void restoreState(JsonValue state) {
        boolean finished = state.getBoolean("finished", false);
        if (finished) {
            this.gameFinished = true;
            this.winnerId = state.getInt("winnerId", 0);
            resultOverlay.show(winnerId, myId, false, boardScreen);
            return;
        }

        // Восстановление прогресса
        this.score = state.getInt("score", 0);
        int currentQuestion = state.getInt("currentQuestionNumber", 1);
        this.totalQuestions = state.getInt("totalQuestions", 15); // может не быть, оставим текущее
        boolean isDone = state.getBoolean("isDone", false);
        if (isDone) {
            this.gameFinished = true;
            return;
        }

        // Восстановление времени
        float remainingTime = state.getFloat("timeLeft", 22f);
        this.timeLeft = remainingTime;

        // Восстановление целевого цвета
        String targetColor = state.getString("targetColor", "RED");
        this.targetColorKey = targetColor;
        updateTargetColorText(targetColor);

        // Настройка состояния UI
        this.waitingForNext = false;
        this.isWaitingForOthers = false;
        this.gameFinished = false;
    }
    @Override public boolean isFinished() { return gameFinished; }
    @Override public void dispose() { }

}
