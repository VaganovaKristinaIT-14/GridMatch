package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.ox.tictactoe.MainGame;

public class ReconnectingOverlay {
    private final MainGame game;
    private final TextureRegion bgPixel;
    private boolean visible = false;
    private float elapsed = 0;
    private final float MAX_TIME = 60f;

    public ReconnectingOverlay(MainGame game) {
        this.game = game;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0.85f);
        pixmap.fill();
        this.bgPixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();
    }

    public void show() {
        visible = true;
        elapsed = 0;
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void update(float delta) {
        if (visible) elapsed += delta;
    }

    public float getTimeLeft() {
        return Math.max(0, MAX_TIME - elapsed);
    }

    public void draw(SpriteBatch batch, BitmapFont font) {
        if (!visible) return;

        // Затемнение на весь экран (1080×1920)
        batch.setColor(0, 0, 0, 0.7f);
        batch.draw(bgPixel, 0, 0, 1080, 1920);
        batch.setColor(Color.WHITE);

        if (font != null) {
            font.setColor(Color.WHITE);
            GlyphLayout layout = new GlyphLayout();
            font.getData().setScale(1.8f);
            String msg = "СОЕДИНЕНИЕ ПОТЕРЯНО";
            layout.setText(font, msg);
            font.draw(batch, msg, (1080 - layout.width) / 2f, 1100f);

            font.getData().setScale(1.2f);
            String timerMsg = String.format("Переподключение через %d сек", (int) getTimeLeft());
            layout.setText(font, timerMsg);
            font.draw(batch, timerMsg, (1080 - layout.width) / 2f, 900f);

            font.getData().setScale(1f);
        }
    }
}
