package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;

public class LobbyOverlay {
    private final MainGame game;
    private final TextureRegion bgPixel; // Для затемнения

    // Текстуры из атласа
    private TextureRegion woodBoard, lobbyTitle, lobbyText, lobbyCheck, btnExit;
    private TextureRegion bt2, bt3, bt4, bt2x2, bt3x3, bt4x4;

    private boolean visible = false;
    private final float WORLD_WIDTH = 1080f;
    private final float WORLD_HEIGHT = 1920f;

    public LobbyOverlay(MainGame game) {
        this.game = game;

        // Создаем белый пиксель для прозрачного фона
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.bgPixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        loadAssets();
    }

    private void loadAssets() {
        // Загружаем всё строго по именам со скрина (без .png)
        woodBoard = game.atlas.findRegion("wood_board");
        lobbyTitle = game.atlas.findRegion("lobby_title");
        lobbyText = game.atlas.findRegion("lobby_text");
        lobbyCheck = game.atlas.findRegion("lobby_check");
        btnExit = game.atlas.findRegion("button_exit");

        bt2 = game.atlas.findRegion("lobby_bt_2");
        bt3 = game.atlas.findRegion("lobby_bt_3");
        bt4 = game.atlas.findRegion("lobby_bt_4");
        bt2x2 = game.atlas.findRegion("lobby_bt_2x2");
        bt3x3 = game.atlas.findRegion("lobby_bt_3x3");
        bt4x4 = game.atlas.findRegion("lobby_bt_4x4");
    }

    public void show() { this.visible = true; }
    public void hide() { this.visible = false; }
    public boolean isVisible() { return visible; }

    public void draw(SpriteBatch batch) {
        if (!visible) return;

        // 1. Отрисовка затемнения на весь экран
        batch.setColor(0, 0, 0, 0.7f);
        batch.draw(bgPixel, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        batch.setColor(Color.WHITE);

        // 2. Отрисовка подложки и текстов
        drawSmart(batch, woodBoard);
        drawSmart(batch, lobbyTitle);
        drawSmart(batch, lobbyText);

        // 3. Отрисовка кнопок количества игроков (2, 3, 4)
        if (bt2 != null) drawSmart(batch, bt2);
        if (bt3 != null) drawSmart(batch, bt3);
        if (bt4 != null) drawSmart(batch, bt4);

        // 4. Отрисовка кнопок размера поля (2x2, 3x3, 4x4)
        if (bt2x2 != null) drawSmart(batch, bt2x2);
        if (bt3x3 != null) drawSmart(batch, bt3x3);
        if (bt4x4 != null) drawSmart(batch, bt4x4);

        // 5. Отрисовка интерфейса (чекбокс/подтверждение и выход)
        drawSmart(batch, lobbyCheck);
        drawSmart(batch, btnExit);
    }

    /**
     * Отрисовка через офсеты из атласа (позиции из Фигмы)
     */
    private void drawSmart(SpriteBatch batch, TextureRegion region) {
        if (region == null) return;

        if (region instanceof TextureAtlas.AtlasRegion) {
            TextureAtlas.AtlasRegion ar = (TextureAtlas.AtlasRegion) region;
            batch.draw(
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

    public void handleInput(Viewport viewport) {
        if (!visible || !Gdx.input.justTouched()) return;

        Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touch);

        // Тут будут методы для кликов по кнопкам
        Gdx.app.log("LOBBY", "Клик в координатах: " + touch.x + ", " + touch.y);
    }
}
