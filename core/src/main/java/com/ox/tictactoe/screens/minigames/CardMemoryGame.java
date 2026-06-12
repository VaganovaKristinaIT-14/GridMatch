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
import com.badlogic.gdx.utils.Align; // <-- Обязательный импорт для выравнивания
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;
import com.ox.tictactoe.PlayerModel;
import com.ox.tictactoe.screens.BoardResultOverlay;
import com.ox.tictactoe.screens.BoardScreen;
import com.ox.tictactoe.screens.GameResultOverlay;

import java.util.ArrayList;
import java.util.List;

public class CardMemoryGame extends ScreenAdapter implements MiniGameInterface {

    private final MainGame game;
    private final BoardScreen boardScreen;

    private OrthographicCamera camera;
    private Viewport viewport;
    private GameResultOverlay resultOverlay;

    private TextureRegion background, textTitle, moonCard, sunCard, starCard, cloudCard;
    private GlyphLayout glyphLayout;

    private int myId = -1;
    private int winnerId = -1;

    private boolean waitingForNext;
    private boolean gameFinished;
    private boolean isWaitingForOthers = false;

    private float timeLeft = 45f;
    private int score = 0;
    private int totalRounds = 3;
    private int currentRound = 0;

    // Логика последовательности
    private List<String> targetSequence = new ArrayList<>();
    private List<String> currentInput = new ArrayList<>();
    private String displaySequenceText = "Загрузка...";

    // Таймеры вспышки для карточек
    private float flashLune = 0f, flashSun = 0f, flashStar = 0f, flashCloud = 0f;
    private final float FLASH_DURATION = 0.3f;

    private Rectangle rectMoon, rectSun, rectStar, rectCloud;

    public CardMemoryGame(MainGame game, BoardScreen boardScreen, int col, int row) {
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
        textTitle = game.atlas.findRegion("card_text");

        moonCard = game.atlas.findRegion("card_lune");
        sunCard = game.atlas.findRegion("card_sun");
        starCard = game.atlas.findRegion("card_star");
        cloudCard = game.atlas.findRegion("card_cloud");

        gameFinished = false;
        waitingForNext = true;
        isWaitingForOthers = false;

        // Расчёт сетки карточек (2x2) точно как на скриншоте
        float cardWidth = 360f;
        float cardHeight = 508f;
        float gap = 40f;

        float totalWidth = (cardWidth * 2) + gap;
        float startX = (1080 - totalWidth) / 2f;

        float bottomY = 300f;
        float topY = bottomY + cardHeight + gap;

        // Верхний ряд (Луна, Солнце)
        rectMoon = new Rectangle(startX, topY, cardWidth, cardHeight);
        rectSun = new Rectangle(startX + cardWidth + gap, topY, cardWidth, cardHeight);

        // Нижний ряд (Звезда, Облако)
        rectStar = new Rectangle(startX, bottomY, cardWidth, cardHeight);
        rectCloud = new Rectangle(startX + cardWidth + gap, bottomY, cardWidth, cardHeight);
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

        // Обновляем таймеры вспышек карточек
        if (flashLune > 0) flashLune -= delta;
        if (flashSun > 0) flashSun -= delta;
        if (flashStar > 0) flashStar -= delta;
        if (flashCloud > 0) flashCloud -= delta;

        handleInput();
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (resultOverlay.isVisible()) {
            resultOverlay.draw(batch);
            return;
        }

        if (background != null) batch.draw(background, 0, 0, 1080, 1920);

        // Рисуем картинку заголовка "Нажми в правильном порядке!"
        if (textTitle != null) {
            float titleX = (1080 - textTitle.getRegionWidth()) / 2f;
            batch.draw(textTitle, titleX, 1550f);
        }

        // --- Отрисовка карточек с подсветкой ---
        drawCard(batch, moonCard, rectMoon, flashLune > 0);
        drawCard(batch, sunCard, rectSun, flashSun > 0);
        drawCard(batch, starCard, rectStar, flashStar > 0);
        drawCard(batch, cloudCard, rectCloud, flashCloud > 0);

        // --- Отрисовка текстов ---
        if (game.mainFont != null) {
            game.mainFont.getData().setScale(1.2f);

            // Таймер и счет наверху
            game.mainFont.setColor(Color.WHITE);
            String topString = "Время: " + (int)timeLeft + " сек    Очки: " + score + " из " + totalRounds;
            glyphLayout.setText(game.mainFont, topString);
            game.mainFont.draw(batch, topString, (1080 - glyphLayout.width) / 2f, 1800f);

            if (isWaitingForOthers) {
                game.mainFont.setColor(Color.YELLOW);
                game.mainFont.getData().setScale(1.5f);
                glyphLayout.setText(game.mainFont, "Подождите других\nигроков...", Color.YELLOW, 950f, Align.center, true);
                game.mainFont.draw(batch, glyphLayout, (1080 - 950f) / 2f, 1550f);
            } else if (!waitingForNext) {// Текст последовательности (красный) с автоматическим переносом!
                game.mainFont.setColor(Color.RED);
                game.mainFont.getData().setScale(1.5f);

                // Максимальная ширина текста (оставляем поля по краям экрана)
                float targetWidth = 950f;

                // LibGDX автоматически перенесет текст на новую строчку и отцентрирует
                glyphLayout.setText(game.mainFont, displaySequenceText, Color.RED, targetWidth, Align.center, true);

                // Рисуем текст
                game.mainFont.draw(batch, glyphLayout, (1080 - targetWidth) / 2f, 1550f);
            }
        }
    }

    // Вспомогательный метод для красивой отрисовки карточки с зеленой вспышкой
    private void drawCard(SpriteBatch batch, TextureRegion region, Rectangle rect, boolean isFlashing) {
        if (region == null) return;
        if (isFlashing) {
            batch.setColor(Color.GREEN); // Красим в зеленый
        } else {
            batch.setColor(Color.WHITE); // Обычный цвет
        }
        batch.draw(region, rect.x, rect.y, rect.width, rect.height);
        batch.setColor(Color.WHITE); // Сбрасываем цвет обратно
    }

    @Override
    public void handleInput() {
        if (game.isInputBlocked()) return;
        if (gameFinished || waitingForNext || targetSequence.isEmpty()) return;

        if (Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);

            String clickedCard = null;

            if (rectMoon.contains(touch.x, touch.y)) {
                clickedCard = "LUNE";
                flashLune = FLASH_DURATION;
            } else if (rectSun.contains(touch.x, touch.y)) {
                clickedCard = "SUN";
                flashSun = FLASH_DURATION;
            } else if (rectStar.contains(touch.x, touch.y)) {
                clickedCard = "STAR";
                flashStar = FLASH_DURATION;
            } else if (rectCloud.contains(touch.x, touch.y)) {
                clickedCard = "CLOUD";
                flashCloud = FLASH_DURATION;
            }

            if (clickedCard != null) {
                currentInput.add(clickedCard);

                // Если ввели нужное количество карточек - отправляем массив на сервер
                if (currentInput.size() == targetSequence.size()) {
                    sendSequenceToServer();
                }
            }
        }
    }

    private void sendSequenceToServer() {
        waitingForNext = true;

        // Вручную собираем JSON массив, чтобы избежать багов LibGDX парсера
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < currentInput.size(); i++) {
            sb.append("\"").append(currentInput.get(i)).append("\"");
            if (i < currentInput.size() - 1) sb.append(",");
        }
        sb.append("]");

        String json = String.format("{\"type\":\"MINIGAME_ACTION\", \"data\":{\"action\":\"SUBMIT\", \"sequence\":%s}}", sb.toString());
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

                // ИГРОК ЗАКОНЧИЛ, ЖДЁТ ДРУГИХ
                if (gameAction.equals("WAITING_FOR_OTHERS")) {
                    isWaitingForOthers = true;
                    if (data.has("score")) {
                        score = data.getInt("score");
                    }
                    return;
                }

                if (gameAction.equals("START") || gameAction.equals("NEXT_ROUND")) {
                    isWaitingForOthers = false;
                    if (data.has("timeLeft")) timeLeft = data.getFloat("timeLeft");
                    else if (gameAction.equals("START")) timeLeft = data.getFloat("time", 45f);

                    if (data.has("score")) score = data.getInt("score");
                    if (data.has("totalRounds")) totalRounds = data.getInt("totalRounds");

                    // Читаем массив последовательности
                    targetSequence.clear();
                    JsonValue seqJson = data.get("targetSequence");
                    if (seqJson != null && seqJson.isArray()) {
                        for (JsonValue v : seqJson) {
                            targetSequence.add(v.asString());
                        }
                    }

                    buildDisplayText();
                    currentInput.clear(); // Сбрасываем наши нажатия для нового раунда
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
                Gdx.app.error("CARD_MEMORY", "Ошибка парсинга: " + e.getMessage());
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

    // Перевод кодов сервера в русские названия для вывода на экран
    private void buildDisplayText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targetSequence.size(); i++) {
            String card = targetSequence.get(i);
            switch (card) {
                case "LUNE": sb.append("Луна"); break;
                case "SUN": sb.append("Солнце"); break;
                case "STAR": sb.append("Звезда"); break;
                case "CLOUD": sb.append("Облако"); break;
            }
            if (i < targetSequence.size() - 1) {
                sb.append(" - ");
            }
        }
        displaySequenceText = sb.toString();
    }

    @Override public boolean isFinished() { return gameFinished; }
    @Override public void dispose() { }

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
        this.currentRound = state.getInt("currentRound", 0);
        boolean isDone = state.getBoolean("isDone", false);
        if (isDone) {
            this.gameFinished = true;
            return;
        }

        // Восстановление времени
        float remainingTime = state.getFloat("timeLeft", 45f);
        this.timeLeft = remainingTime;

        // Восстановление общего количества раундов
        this.totalRounds = state.getInt("totalRounds", 3);

        // Восстановление целевой последовательности
        JsonValue seqJson = state.get("targetSequence");
        if (seqJson != null && seqJson.isArray()) {
            this.targetSequence.clear();
            for (JsonValue v : seqJson) {
                this.targetSequence.add(v.asString());
            }
            buildDisplayText();
        }

        // Настройка состояния UI
        this.waitingForNext = false;
        this.isWaitingForOthers = false;
        this.gameFinished = false;
        this.currentInput.clear();
    }
}
