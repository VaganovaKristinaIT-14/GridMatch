package com.ox.tictactoe.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.ox.tictactoe.MainGame;

public class GameResultOverlay {
    private final MainGame game;

    private TextureRegion backgroundAll; // Та самая подложка "all"
    private TextureRegion miniWin, miniLose, boardWin, boardLose, currentResultTexture;

    private boolean visible = false;
    private float displayTimer = 0;
    private final float MAX_DISPLAY_TIME = 5.0f;
    private BoardScreen boardScreen;

    public GameResultOverlay(MainGame game) {
        this.game = game;

        // Загружаем фон "all" из твоего атласа
        this.backgroundAll = game.allBackTexture;

        // Загружаем плашки
        this.miniWin = game.atlas.findRegion("window_win");
        this.miniLose = game.atlas.findRegion("window_lose");
        this.boardWin = game.atlas.findRegion("board_win");
        this.boardLose = game.atlas.findRegion("board_lose");
    }

    public void show(int winnerId, int myId, boolean isGlobal, BoardScreen board) {
        this.visible = true;
        this.boardScreen = board;
        this.displayTimer = 0;

        if (winnerId == 0) {
            currentResultTexture = null;
        } else if (winnerId == myId) {
            currentResultTexture = isGlobal ? boardWin : miniWin;
        } else {
            currentResultTexture = isGlobal ? boardLose : miniLose;
        }
    }

    public void update(float delta) {
        if (!visible) return;
        displayTimer += delta;
        if (displayTimer >= MAX_DISPLAY_TIME) {
            visible = false;
            if (boardScreen != null) {
                game.setScreen(boardScreen);
            }
        }
    }

    public void draw(SpriteBatch batch) {
        if (!visible) return;

        // 1. Сбрасываем цвет в чистый белый, чтобы картинки были без искажений
        batch.setColor(Color.WHITE);

        // 2. Рисуем фоновую подложку "all" на весь экран
        if (backgroundAll != null) {
            batch.draw(backgroundAll, 0, 0, 1080, 1920);
        }

        // 3. Рисуем саму плашку по центру
        if (currentResultTexture != null) {
            float width = currentResultTexture.getRegionWidth();
            float height = currentResultTexture.getRegionHeight();
            float x = (1080 - width) / 2f;
            float y = (1920 - height) / 2f;
            batch.draw(currentResultTexture, x, y, width, height);
        } else {
            if (game.font != null) {
                game.font.draw(batch, "DRAW", 450, 1000);
            }
        }
    }

    public boolean isVisible() { return visible; }
}
