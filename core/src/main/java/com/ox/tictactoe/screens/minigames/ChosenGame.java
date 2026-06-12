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

public class ChosenGame extends ScreenAdapter implements MiniGameInterface {

    private final MainGame game;
    private final BoardScreen boardScreen;
    private final int col, row;

    private OrthographicCamera camera;
    private Viewport viewport;

    private TextureRegion background, board, field1, field2, textTitle;
    private GlyphLayout glyphLayout;

    // Оверлей для результатов мини-игры
    private GameResultOverlay resultOverlay;

    private boolean gameFinished;
    private boolean waitingForNext;
    private boolean isWaitingForOthers = false; // НОВЫЙ ФЛАГ

    private int score = 0;
    private float timeLeft = 45f;
    private String leftExpression = "";
    private String rightExpression = "";
    private int currentQuestion = 1;
    private int totalQuestions = 15;

    // Идентификаторы
    private int myId = -1;
    private int winnerId = -1;

    private Rectangle leftRect, rightRect;

    public ChosenGame(MainGame game, BoardScreen boardScreen, int col, int row) {
        this.game = game;
        this.boardScreen = boardScreen;
        this.col = col;
        this.row = row;

        camera = new OrthographicCamera();
        viewport = new FitViewport(1080, 1920, camera);
        camera.position.set(1080 / 2f, 1920 / 2f, 0);
        glyphLayout = new GlyphLayout();

        // Инициализируем оверлей
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
        board = game.atlas.findRegion("chosengame_board");
        field1 = game.atlas.findRegion("chosengame_field");
        field2 = game.atlas.findRegion("chosengame_field");
        textTitle = game.atlas.findRegion("chosengame_text");

        gameFinished = false;
        waitingForNext = true;
        isWaitingForOthers = false;

        float boardY = 600f;
        float paddingX = 15f;

        leftRect = new Rectangle(1080 / 2f - 472 - paddingX, boardY + 250f, 472, 217);
        rightRect = new Rectangle(1080 / 2f + paddingX - 25f, boardY + 250f, 472, 217);
    }

    @Override
    public void update(float delta) {
        // Если игра завершена, обновляем только оверлей (он сам вернет нас на доску)
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
        // Если оверлей активен, рисуем ТОЛЬКО ЕГО (он перекрывает весь экран)
        if (resultOverlay.isVisible()) {
            resultOverlay.draw(batch);
            return;
        }

        // --- Отрисовка основной игры ---
        if (background != null) batch.draw(background, 0, 0, 1080, 1920);

        float boardX = (1080 - (board != null ? board.getRegionWidth() : 0)) / 2f;
        float boardY = 600f;
        if (board != null) batch.draw(board, boardX, boardY);

        float titleX = (1080 - (textTitle != null ? textTitle.getRegionWidth() : 0)) / 2f;
        float titleY = boardY + (board != null ? board.getRegionHeight() : 0) + 30f;
        if (textTitle != null) batch.draw(textTitle, titleX, titleY);

        if (field1 != null) batch.draw(field1, leftRect.x, leftRect.y);
        if (field2 != null) batch.draw(field2, rightRect.x, rightRect.y);

        if (game.mainFont != null) {
            game.mainFont.getData().setScale(1.2f);
            game.mainFont.setColor(Color.WHITE);

            String progress = "Вопрос " + currentQuestion + " из " + totalQuestions;
            glyphLayout.setText(game.mainFont, progress);
            game.mainFont.draw(batch, progress, (1080 - glyphLayout.width) / 2f, boardY + 680f);

            String topText = "Время: " + (int)timeLeft + " сек    Очки: " + score;
            glyphLayout.setText(game.mainFont, topText);
            game.mainFont.draw(batch, topText, (1080 - glyphLayout.width) / 2f, boardY - 60f);

            if (isWaitingForOthers) {
                game.mainFont.setColor(Color.YELLOW);
                game.mainFont.getData().setScale(1.2f);
                glyphLayout.setText(game.mainFont, "Подождите других\nигроков...", Color.YELLOW, 950f, Align.center, true);
                // Рисуем текст прямо поверх деревянной доски по центру
                game.mainFont.draw(batch, glyphLayout, (1080 - 950f) / 2f, boardY + 250f);
            } else if (!waitingForNext) {
                glyphLayout.setText(game.mainFont, leftExpression);
                game.mainFont.draw(batch, leftExpression,
                    leftRect.x + (leftRect.width - glyphLayout.width) / 2f,
                    leftRect.y + (leftRect.height + glyphLayout.height) / 2f);

                glyphLayout.setText(game.mainFont, rightExpression);
                game.mainFont.draw(batch, rightExpression,
                    rightRect.x + (rightRect.width - glyphLayout.width) / 2f,
                    rightRect.y + (rightRect.height + glyphLayout.height) / 2f);
            }
        }
    }

    @Override
    public void handleInput() {
        if (game.isInputBlocked()) return;
        if (gameFinished) return;

        if (Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);

            if (waitingForNext) return;

            if (leftRect.contains(touch.x, touch.y)) {
                sendAction("LEFT");
            } else if (rightRect.contains(touch.x, touch.y)) {
                sendAction("RIGHT");
            }
        }
    }

    private void sendAction(String choice) {
        waitingForNext = true;
        String json = String.format("{\"type\":\"MINIGAME_ACTION\", \"data\":{\"action\":\"%s\"}}", choice);
        game.gameClient.send(json);
    }

    @Override
    public void handleNetworkMessage(JsonValue json) {
        String type = json.getString("type", "");

        if (type.equals("MINIGAME_ACTION")) {
            JsonValue data = json.get("data");
            if (data == null) return;

            String gameAction = data.getString("gameAction", "");

            // ОБРАБОТКА СИНХРОНИЗАЦИИ ВРЕМЕНИ
            if (gameAction.equals("TIME_SYNC")) {
                this.timeLeft = data.getFloat("timeLeft");
                return; // Выходим, так как это просто обновление времени
            }

            // ИГРОК ЗАКОНЧИЛ, ЖДЁТ ДРУГИХ
            if (gameAction.equals("WAITING_FOR_OTHERS")) {
                isWaitingForOthers = true;
                if (data.has("score")) {
                    score = data.getInt("score");
                }
                return;
            }

            try {
                if (gameAction.equals("START") || gameAction.equals("NEXT_QUESTION")) {
                    isWaitingForOthers = false;
                    if (data.has("timeLeft")) {
                        timeLeft = data.getFloat("timeLeft");
                    } else if (gameAction.equals("START")) {
                        timeLeft = data.getFloat("time", 45f);
                        totalQuestions = data.getInt("totalQuestions", 15);
                    }
                    leftExpression = data.getString("leftText", "");
                    rightExpression = data.getString("rightText", "");
                    currentQuestion = data.getInt("questionNum", 1);

                    if (data.has("score")) {
                        score = data.getInt("score");
                    }

                    waitingForNext = false;
                }
                else if (gameAction.equals("GAME_OVER")) {
                    if (!gameFinished) {
                        gameFinished = true;
                        if (data.has("winnerId")) {
                            winnerId = data.getInt("winnerId");
                        }
                        // Показываем стандартный оверлей (isGlobal = false, так как это мини-игра)
                        resultOverlay.show(winnerId, myId, false, boardScreen);
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("CHOSEN", "Ошибка парсинга: " + e.getMessage());
                waitingForNext = false;
            }
        }

        // В случае если сервер прислал финиш через общий MINIGAME_RESULT
        if (type.equals("MINIGAME_RESULT")) {
            if (!gameFinished) {
                gameFinished = true;
                JsonValue data = json.get("data");
                if (data != null && data.has("minigameWinnerId")) {
                    winnerId = data.getInt("minigameWinnerId");
                }
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

    @Override
    public boolean isFinished() { return gameFinished; }

    @Override
    public void dispose() { }

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
        this.currentQuestion = state.getInt("currentQuestionNumber", 1);
        this.totalQuestions = state.getInt("totalQuestions", 15);
        boolean isDone = state.getBoolean("isDone", false);
        if (isDone) {
            this.gameFinished = true;
            return;
        }

        // Восстановление времени
        float remainingTime = state.getFloat("timeLeft", 45f);
        this.timeLeft = remainingTime;

        // Восстановление выражений
        this.leftExpression = state.getString("leftText", "");
        this.rightExpression = state.getString("rightText", "");

        // Настройка состояния UI
        this.waitingForNext = false;
        this.isWaitingForOthers = false;
        this.gameFinished = false;
    }
}
