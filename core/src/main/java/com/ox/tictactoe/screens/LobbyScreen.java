package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.LoadingScreen;
import com.ox.tictactoe.MainGame;

public class LobbyScreen extends ScreenAdapter {
    private final MainGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;

    private final float WORLD_WIDTH = 1080f;
    private final float WORLD_HEIGHT = 1920f;

    // Текстуры из атласа
    private TextureRegion background;
    private TextureRegion woodBoard;
    private TextureRegion lobbyTitle;
    private TextureRegion lobbyText;
    private TextureRegion lobbyCheck;
    private TextureRegion btnExit;
    private TextureRegion btn2, btn3, btn4;
    private TextureRegion btn2x2, btn3x3, btn4x4;

    // Состояние выбора
    private int selectedPlayers = 2;
    private int selectedBoardSize = 3;

    // Координаты кликов (работаем через offsets из атласа)
    public LobbyScreen(MainGame game) {
        this.game = game;
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();

        loadTextures();
    }

    private void loadTextures() {
        background = game.allBackTexture;
        woodBoard = game.atlas.findRegion("wood_board");
        lobbyTitle = game.atlas.findRegion("lobby_title");
        lobbyText = game.atlas.findRegion("lobby_text");
        lobbyCheck = game.atlas.findRegion("lobby_check");
        btnExit = game.atlas.findRegion("button_exit");

        com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> btRegions = game.atlas.findRegions("lobby_bt");
        if (btRegions != null && btRegions.size >= 3) {
            btn2 = btRegions.get(0);
            btn3 = btRegions.get(1);
            btn4 = btRegions.get(2);
        } else {
            // fallback если не нашло
            btn2 = game.atlas.findRegion("lobby_bt");
            btn3 = game.atlas.findRegion("lobby_bt");
            btn4 = game.atlas.findRegion("lobby_bt");
        }

        btn2x2 = game.atlas.findRegion("lobby_bt_2x2");
        btn3x3 = game.atlas.findRegion("lobby_bt_3x3");
        btn4x4 = game.atlas.findRegion("lobby_bt_4x4");
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // Фон
        if (background != null) {
            game.batch.draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        }

        // Деревянная доска
        drawRegion(woodBoard);

        // Заголовок и текст
        drawRegion(lobbyTitle);
        drawRegion(lobbyText);

        // Кнопки количества игроков
        drawButton(btn2, selectedPlayers == 2);
        drawButton(btn3, selectedPlayers == 3);
        drawButton(btn4, selectedPlayers == 4);

        // Кнопки размера доски
        drawButton(btn2x2, selectedBoardSize == 2);
        drawButton(btn3x3, selectedBoardSize == 3);
        drawButton(btn4x4, selectedBoardSize == 4);

        // Кнопка подтверждения
        drawRegion(lobbyCheck);
        drawRegion(btnExit);

        game.batch.end();
        handleInput();
    }

    private void drawRegion(TextureRegion region) {
        if (region == null) return;
        if (region instanceof TextureAtlas.AtlasRegion) {
            TextureAtlas.AtlasRegion ar = (TextureAtlas.AtlasRegion) region;
            game.batch.draw(
                ar.getTexture(),
                ar.offsetX, ar.offsetY,
                0, 0,
                ar.packedWidth, ar.packedHeight,
                1, 1, 0,
                ar.getRegionX(), ar.getRegionY(),
                ar.getRegionWidth(), ar.getRegionHeight(),
                false, false
            );
        }
    }

    private void drawButton(TextureRegion region, boolean selected) {
        if (region == null) return;
        if (selected) {
            game.batch.setColor(Color.GREEN);
        } else {
            game.batch.setColor(Color.WHITE);
        }
        drawRegion(region);
        game.batch.setColor(Color.WHITE);
    }

    private boolean isTouched(TextureRegion region, float tx, float ty) {
        if (region == null) return false;
        if (!(region instanceof TextureAtlas.AtlasRegion)) return false;
        TextureAtlas.AtlasRegion ar = (TextureAtlas.AtlasRegion) region;
        return tx >= ar.offsetX && tx <= ar.offsetX + ar.packedWidth &&
            ty >= ar.offsetY && ty <= ar.offsetY + ar.packedHeight;
    }

    private void handleInput() {
        if (game.isInputBlocked()) return;
        if (!Gdx.input.justTouched()) return;

        Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touch);

        // Выбор количества игроков
        if (isTouched(btn2, touch.x, touch.y)) {
            selectedPlayers = 2;
        } else if (isTouched(btn3, touch.x, touch.y)) {
            selectedPlayers = 3;
        } else if (isTouched(btn4, touch.x, touch.y)) {
            selectedPlayers = 4;
        }
        // Выбор размера доски
        else if (isTouched(btn2x2, touch.x, touch.y)) {
            selectedBoardSize = 2;
        } else if (isTouched(btn3x3, touch.x, touch.y)) {
            selectedBoardSize = 3;
        } else if (isTouched(btn4x4, touch.x, touch.y)) {
            selectedBoardSize = 4;
        }
        // Кнопка создания лобби
        else if (isTouched(lobbyCheck, touch.x, touch.y)) {
            game.gameClient.sendCreateLobby(selectedPlayers, selectedBoardSize);
            game.setScreen(new LoadingScreen(game));
        }
        // Кнопка выхода (если нужна)
        else if (isTouched(btnExit, touch.x, touch.y)) {
            game.setScreen(new MenuScreen(game));
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
