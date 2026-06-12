package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;

public class BoardExitOverlay {
    private final MainGame game;
    private final BoardScreen boardScreen;
    private final TextureRegion bgPixel;
    private TextureRegion table, btnYes, btnNo;

    private boolean visible = false;
    private Rectangle yesBounds, noBounds;

    public BoardExitOverlay(MainGame game, BoardScreen boardScreen) {
        this.game = game;
        this.boardScreen = boardScreen;

        // Создаем белую точку для затемнения фона
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.bgPixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        table = game.atlas.findRegion("exit_board_table");
        btnYes = game.atlas.findRegion("exit_board_yes");
        btnNo = game.atlas.findRegion("exit_board_no");

        // Координаты окна (центрирование на экране 1080x1920)
        float tw = 800, th = 500;
        float tx = (1080 - tw) / 2f;
        float ty = (1920 - th) / 2f;

        // Области клика: Yes слева, No справа
        yesBounds = new Rectangle(tx + 80, ty + 100, 300, 130);
        noBounds = new Rectangle(tx + tw - 380, ty + 100, 300, 130);
    }

    public void show() { visible = true; }
    public void hide() { visible = false; }
    public boolean isVisible() { return visible; }

    public void draw(SpriteBatch batch) {
        if (!visible) return;

        // Рисуем затемнение
        batch.setColor(0, 0, 0, 0.6f);
        batch.draw(bgPixel, 0, 0, 1080, 1920);
        batch.setColor(Color.WHITE);

        float tw = 800, th = 500;
        float tx = (1080 - tw) / 2f;
        float ty = (1920 - th) / 2f;

        if (table != null) batch.draw(table, tx, ty, tw, th);
        if (btnYes != null) batch.draw(btnYes, yesBounds.x, yesBounds.y, yesBounds.width, yesBounds.height);
        if (btnNo != null) batch.draw(btnNo, noBounds.x, noBounds.y, noBounds.width, noBounds.height);
    }

    public void handleInput(Viewport viewport) {
        if (!visible) return;

        if (Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);

            if (yesBounds.contains(touch.x, touch.y)) {
                hide();
                // 1. Сначала говорим нашему экрану, что МЫ выходим (чтобы увидеть плашку LOSE)
                boardScreen.onPlayerLeft(game.myResourceId);

                // 2. Рвем соединение. Сервер получит CloseStatus и оповестит другого игрока
                if (game.gameClient != null && game.gameClient.isOpen()) {
                    game.gameClient.sendLeaveRoom();
                }
            } else if (noBounds.contains(touch.x, touch.y)) {
                hide();
            }
        }
    }
}
