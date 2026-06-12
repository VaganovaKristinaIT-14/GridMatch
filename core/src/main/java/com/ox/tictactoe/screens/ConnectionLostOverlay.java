package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;

public class ConnectionLostOverlay {
    private final MainGame game;
    private final Viewport viewport;

    private final TextureRegion bgPixel;
    private final TextureRegion table;
    private final TextureRegion btnExit;

    private final Rectangle btnBounds;

    public ConnectionLostOverlay(MainGame game) {
        this.game = game;
        // Используем стандартное разрешение 1080x1920
        this.viewport = new FitViewport(1080, 1920);

        // Создаем черный полупрозрачный пиксель для затемнения фона на 85%
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0.85f);
        pixmap.fill();
        this.bgPixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        // Берем НОВЫЕ текстуры из атласа
        this.table = game.atlas.findRegion("error_conect_table");
        this.btnExit = game.atlas.findRegion("error_conect_exit");

        // Центрируем таблицу
        float tableH = 747f;
        float tableY = (1920 - tableH) / 2f;

        // Кнопка выхода (error_conect_exit) находится внутри таблицы внизу
        float btnW = 421f;
        float btnH = 165f;
        float btnX = (1080 - btnW) / 2f; // По центру по горизонтали
        float btnY = tableY + 100f;      // Отступ от нижнего края таблички

        this.btnBounds = new Rectangle(btnX, btnY, btnW, btnH);
    }

    public void render() {
        viewport.apply();
        game.batch.setProjectionMatrix(viewport.getCamera().combined);
        game.batch.begin();

        // 1. Темный фон на весь экран
        game.batch.draw(bgPixel, 0, 0, 1080, 1920);

        // 2. Рисуем табличку "Нет подключения к сети"
        if (table != null) {
            float tableW = table.getRegionWidth();
            float tableH = table.getRegionHeight();
            game.batch.draw(table, (1080 - tableW) / 2f, (1920 - tableH) / 2f);
        }

        // 3. Рисуем светящуюся неоновую кнопку "Выйти"
        if (btnExit != null) {
            game.batch.draw(btnExit, btnBounds.x, btnBounds.y, btnBounds.width, btnBounds.height);
        }

        game.batch.end();

        handleInput();
    }

    private void handleInput() {
        if (Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);

            // Если кликнули на неоновую кнопку "Выйти" -> закрываем приложение
            if (btnBounds.contains(touch.x, touch.y)) {
                System.out.println("[СИСТЕМА] Выход из игры по причине потери соединения.");
                Gdx.app.exit();
            }
        }
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        viewport.getCamera().position.set(1080 / 2f, 1920 / 2f, 0);
    }
}
