package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;

public class BoardScreen extends ScreenAdapter {
    private final MainGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;

    private TextureRegion mainBg, boardBack, boardCell, boardLosa;
    private TextureRegion iconSun, iconTree, iconWolf, iconBird;
    private TextureRegion pixel;

    // Новое: Кнопка выхода
    private TextureRegion btnExitIcon;
    private Rectangle btnExitBounds;
    private BoardExitOverlay exitOverlay;

    private BoardResultOverlay finalOverlay;
    private boolean isGameOver = false;
    public boolean isDrawFinished = false;
    private final float WORLD_WIDTH = 1080f;
    private final float WORLD_HEIGHT = 1920f;

    private int boardSize;
    private float boardWidth = 900f;
    private float cellSize;
    private float startX, startY;
    private int[][] boardState;

    private int pendingCol, pendingRow;
    private boolean isAnimating = false;
    private final Vector3 targetPosition = new Vector3();
    private float targetZoom = 1.0f;
    private final float ANIMATION_SPEED = 5f;

    private float fadeAlpha = 0;
    private boolean isFading = false;
    private final float FADE_SPEED = 2f;
    private float overlayAlpha = 0f;

    private int activePlayerId = -1; // Кто ходит сейчас
    private float inactivityTimer = 0f; // сколько прошло с начала хода (сек)
    private boolean isMyTurn = false;
    private int timeoutLimit = 180;
    public BoardScreen(MainGame game) {
        this.game = game;
        this.game.boardScreen = this;
        this.boardSize = game.currentBoardSize;
        this.boardState = new int[boardSize][boardSize];
        this.finalOverlay = new BoardResultOverlay(game);
        this.exitOverlay = new BoardExitOverlay(game, this);

        this.cellSize = boardWidth / boardSize;
        this.startX = (WORLD_WIDTH - boardWidth) / 2f;
        this.startY = (WORLD_HEIGHT - boardWidth) / 2f;

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);

        // Загрузка
        mainBg = game.allBackTexture;
        boardBack = game.atlas.findRegion("board_back");
        boardCell = game.atlas.findRegion("board_cell");
        boardLosa = game.atlas.findRegion("board_losa");
        iconSun = game.atlas.findRegion("icon_sun");
        iconTree = game.atlas.findRegion("icon_tree");
        iconWolf = game.atlas.findRegion("icon_wolf");
        iconBird = game.atlas.findRegion("icon_bird");

        btnExitIcon = game.atlas.findRegion("board_button_exit");
        // Позиция: правый верхний угол, отступ 40
        btnExitBounds = new Rectangle(WORLD_WIDTH - 160, WORLD_HEIGHT - 160, 120, 120);

        // В конструкторе BoardScreen после загрузки текстур:
        if (btnExitIcon == null) System.out.println("!!! ОШИБКА: board_button_exit не найден в атласе");
        if (iconWolf == null) System.out.println("!!! ОШИБКА: icon_wolf не найден в атласе");
        if (iconBird == null) System.out.println("!!! ОШИБКА: icon_bird не найден в атласе");

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();
        updateCamera(delta);

        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        if (mainBg != null) game.batch.draw(mainBg, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        if (boardBack != null) game.batch.draw(boardBack, startX, startY, boardWidth, boardWidth);

        drawNeonGrid();
        drawOccupiedIcons();

        // 1. УДАЛЕНО: boardLosa больше не рисуется

        if (btnExitIcon != null) {
            game.batch.draw(btnExitIcon, btnExitBounds.x, btnExitBounds.y, btnExitBounds.width, btnExitBounds.height);
        }

        game.batch.end();
        if (isMyTurn && !isGameOver) {
            inactivityTimer += delta;
        }
        renderUI();
        drawFading();

        game.batch.begin();
        if (isGameOver && finalOverlay != null) finalOverlay.draw(game.batch);
        if (exitOverlay != null && exitOverlay.isVisible()) exitOverlay.draw(game.batch);
        game.batch.end();
    }

    private void updateCamera(float delta) {
        if (isAnimating && isFading) {
            camera.position.lerp(targetPosition, ANIMATION_SPEED * delta);
            camera.zoom = MathUtils.lerp(camera.zoom, targetZoom, ANIMATION_SPEED * delta);
            camera.update();

            fadeAlpha += delta * FADE_SPEED;

            // Если анимация забаговалась, насильно завершаем её через 2 секунды
            if (Math.abs(camera.zoom - targetZoom) < 0.01f || fadeAlpha > 2.0f) {
                isAnimating = false;
                isFading = false;
                fadeAlpha = 0f;
                game.setScreen(new com.ox.tictactoe.LoadingScreen(game));
            }
        }
    }

    public void onPlayerLeft(int leftPlayerId) {
        // Если игра уже завершена (победа/ничья), не обрабатываем выход
        if (isGameOver) {
            System.out.println("[BOARD] Игра уже завершена, выход игрока игнорируется");
            return;
        }
        System.out.println("[BOARD] Обработка выхода игрока: " + leftPlayerId);
        int playerCount = game.players.size;
        if (playerCount <= 2) {
            this.isGameOver = true;
            if (finalOverlay != null) {
                if (leftPlayerId != game.myResourceId) {
                    finalOverlay.show(BoardResultOverlay.ResultState.WIN);
                } else {
                    finalOverlay.show(BoardResultOverlay.ResultState.LOSE);
                }
            }
        } else {
            clearPlayerCells(leftPlayerId);
        }
    }
    private void clearPlayerCells(int playerId) {
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (boardState[r][c] == playerId) {
                    boardState[r][c] = 0;
                }
            }
        }
    }

    private void handleInput() {
        if (game.isInputBlocked()) return;

        // Если финальный оверлей видим — обрабатываем только его клики и выходим
        if (isGameOver && finalOverlay != null && finalOverlay.isVisible()) {
            finalOverlay.handleInput(viewport);
            return;
        }

        // Если оверлей выхода видим — обрабатываем только его клики и выходим
        if (exitOverlay != null && exitOverlay.isVisible()) {
            exitOverlay.handleInput(viewport);
            return;
        }

        if (isGameOver || isAnimating) return;

        if (Gdx.input.justTouched()) {
            Vector3 touchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touchPos);

            // Кнопка выхода работает ВСЕГДА
            if (btnExitBounds != null && btnExitBounds.contains(touchPos.x, touchPos.y)) {
                exitOverlay.show();
                return;
            }

            // БЛОКИРОВКА ПОЛЯ: Если не мой ход, дальше не пускаем
            if (activePlayerId != game.myResourceId) {
                Gdx.app.log("BOARD", "Клик заблокирован: сейчас ход игрока " + activePlayerId);
                return;
            }

            // Логика атаки клетки
            float localX = touchPos.x - startX;
            float localY = touchPos.y - startY;
            if (localX >= 0 && localX <= boardWidth && localY >= 0 && localY <= boardWidth) {
                int col = (int) (localX / cellSize);
                int row = (int) (localY / cellSize);
                if (boardState[row][col] == 0) {
                    this.pendingCol = col;
                    this.pendingRow = row;
                    game.gameClient.sendCellAttack(pendingRow, pendingCol);
                    startZoomFadingAnimation();
                }
            }
        }
    }


    private void drawOccupiedIcons() {
        float padding = cellSize * 0.15f;
        float iconSize = cellSize - (padding * 2);

        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                int playerId = boardState[r][c];
                if (playerId == 0) continue;

                // Ищем игрока по ID, чтобы узнать его фишку
                String tokenName = "SUN"; // Дефолт
                for (com.ox.tictactoe.PlayerModel p : game.players) {
                    if (p.id == playerId) {
                        tokenName = p.token;
                        break;
                    }
                }

                // Достаем нужный регион из атласа динамически
                TextureRegion tr = game.atlas.findRegion("icon_" + tokenName.toLowerCase());

                // Если в атласе нет icon_wolf, а есть просто wolf — убери префикс "icon_"
                if (tr == null) tr = game.atlas.findRegion(tokenName.toLowerCase());

                if (tr != null) {
                    game.batch.draw(tr, startX + c * cellSize + padding, startY + r * cellSize + padding, iconSize, iconSize);
                }
            }
        }
    }
    // Отрисовка ников игроков
    private void renderUI() {
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        if (game.mainFont != null) {
            float uiX = 50;
            float uiY = WORLD_HEIGHT - 120;
            game.mainFont.getData().setScale(1.1f);

            for (int i = 0; i < game.players.size; i++) {
                com.ox.tictactoe.PlayerModel p = game.players.get(i);

                // Формируем имя: добавляем (YOU) если это наш ID
                String displayName = p.nickname + (p.id == game.myResourceId ? " (YOU)" : "");

                // Логика цвета: только активный игрок ЗЕЛЕНЫЙ, остальные БЕЛЫЕ
                if (p.id == activePlayerId) {
                    game.mainFont.setColor(Color.GREEN);
                } else {
                    game.mainFont.setColor(Color.WHITE);
                }

                game.mainFont.draw(game.batch, displayName, uiX, uiY);
                uiY -= 60;
            }

            // Верхний статус
            game.mainFont.getData().setScale(1.8f);
            if (activePlayerId == game.myResourceId) {
                game.mainFont.setColor(Color.GREEN);
                game.mainFont.draw(game.batch, "ВАШ ХОД", 0, WORLD_HEIGHT - 40, WORLD_WIDTH, 1, true);
            } else {
                game.mainFont.setColor(Color.WHITE);
                game.mainFont.draw(game.batch, "ОЖИДАНИЕ...", 0, WORLD_HEIGHT - 40, WORLD_WIDTH, 1, true);
            }
            if (isMyTurn && !isGameOver) {
                float timeLeft = timeoutLimit - inactivityTimer;
                if (timeLeft < 0) timeLeft = 0;
                String timerText = String.format("⏱ %d сек", (int) Math.ceil(timeLeft));

                // Сохраняем текущие настройки шрифта
                float oldScale = game.mainFont.getData().scaleX;
                Color oldColor = game.mainFont.getColor().cpy();

                game.mainFont.setColor(Color.YELLOW);
                game.mainFont.getData().setScale(1.2f);

                GlyphLayout layout = new GlyphLayout(game.mainFont, timerText);
                game.mainFont.draw(game.batch, timerText,
                    WORLD_WIDTH - layout.width - 50, WORLD_HEIGHT - 150);

                // Восстанавливаем
                game.mainFont.setColor(oldColor);
                game.mainFont.getData().setScale(oldScale);
            }
        }
        game.batch.end();
    }



    private void drawNeonGrid() {
        if (boardCell == null) return;
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                game.batch.draw(boardCell, startX + c * cellSize, startY + r * cellSize, cellSize, cellSize);
            }
        }
    }

    private void drawFading() {
        if (fadeAlpha <= 0) return;
        game.batch.begin();
        game.batch.setColor(0, 0, 0, fadeAlpha);
        game.batch.draw(pixel, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }
    //Сеттер для ходов
    public void setActivePlayer(int id) {
        this.activePlayerId = id;
        this.isMyTurn = (id == game.myResourceId);
        if (isMyTurn) {
            this.inactivityTimer = 0f;
            this.timeoutLimit = game.turnTimeoutSeconds;
        }
    }

    private void startZoomFadingAnimation() {
        isAnimating = true; isFading = true;
        targetPosition.set(startX + pendingCol*cellSize + cellSize/2f, startY + pendingRow*cellSize + cellSize/2f, 0);
        targetZoom = 0.3f;
    }

    @Override
    public void show() {
        restartCameraPosition();
        fadeAlpha = 0;
        isFading = false;
        isAnimating = false;

        // Синхронизируем ход из мейна при возврате на экран
        if (game.currentActivePlayerId != -1) {
            this.activePlayerId = game.currentActivePlayerId;
        }
        Gdx.app.log("BOARD", "Экран показан. Активный игрок: " + activePlayerId);
    }


    public void restartCameraPosition() { camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0); camera.zoom = 1.0f; camera.update(); }
    public void onCellCaptured(int r, int c, int p) { if (r>=0 && r<boardSize && c>=0 && c<boardSize) { boardState[r][c] = p; checkWinCondition(); } }

    private void checkWinCondition() {
        if (isGameOver) return;
        int w = 0;
        for (int i = 0; i < boardSize; i++) {
            if (checkLine(i, 0, 0, 1)) w = boardState[i][0];
            if (checkLine(0, i, 1, 0)) w = boardState[0][i];
        }
        if (checkLine(0, 0, 1, 1)) w = boardState[0][0];
        if (checkLine(0, boardSize-1, 1, -1)) w = boardState[0][boardSize-1];
        if (w != 0) { isGameOver = true; overlayAlpha = 0; finalOverlay.show(w == game.myResourceId ? BoardResultOverlay.ResultState.WIN : BoardResultOverlay.ResultState.LOSE); }
        else if (isBoardFull()) { isGameOver = true; isDrawFinished = true; overlayAlpha = 0; finalOverlay.show(BoardResultOverlay.ResultState.DRAW); }
    }

    private boolean isBoardFull() {
        for (int r=0; r<boardSize; r++) for (int c=0; c<boardSize; c++) if (boardState[r][c] == 0) return false;
        return true;
    }

    private boolean checkLine(int r, int c, int dr, int dc) {
        int f = boardState[r][c]; if (f == 0) return false;
        for (int i=1; i<boardSize; i++) if (boardState[r+i*dr][c+i*dc] != f) return false;
        return true;
    }
    public void showGameOver(BoardResultOverlay.ResultState state) {
        isGameOver = true;
        overlayAlpha = 0;
        if (finalOverlay != null) {
            finalOverlay.show(state);
        }
    }
    public void restoreBoardState(JsonValue state) {
        // state — массив массивов
        for (int r = 0; r < boardSize; r++) {
            JsonValue row = state.get(r);
            for (int c = 0; c < boardSize; c++) {
                boardState[r][c] = row.getInt(c);
            }
        }
    }
    public void setRemainingTurnTime(float seconds) {
        if (activePlayerId == game.myResourceId && !isGameOver) {
            inactivityTimer = timeoutLimit - seconds;
            if (inactivityTimer < 0) inactivityTimer = 0;
        }
    }
    @Override public void resize(int w, int h) { viewport.update(w, h, true); }
    @Override public void dispose() {}
}
