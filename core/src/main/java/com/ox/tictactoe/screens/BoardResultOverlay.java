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

public class BoardResultOverlay {
    private final MainGame game;
    private final TextureRegion bgPixel;
    private TextureRegion winTexture, loseTexture, drawTexture, btnHome;
    private TextureRegion currentResult;

    public enum ResultState { WIN, LOSE, DRAW }
    private boolean visible = false;
    private Rectangle btnBounds;

    public BoardResultOverlay(MainGame game) {
        this.game = game;

        // Создаем пиксель для затемнения фона
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.bgPixel = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        // Загрузка текстур из атласа
        winTexture = game.atlas.findRegion("result_board_win");
        loseTexture = game.atlas.findRegion("result_board_lose");
        drawTexture = game.atlas.findRegion("result_board_draw"); // Твоя текстура ничьей
        btnHome = game.atlas.findRegion("result_board_tomain");

        // Кнопка "На главную" (размеры и позиция)
        btnBounds = new Rectangle(1080 / 2f - 225, 500, 450, 160);
    }

    public void show(ResultState state) {
        if (state == ResultState.WIN) currentResult = winTexture;
        else if (state == ResultState.LOSE) currentResult = loseTexture;
        else currentResult = drawTexture; // Теперь здесь будет именно картинка ничьей
        visible = true;
    }

    public void draw(SpriteBatch batch) {
        if (!visible) return;

        // 1. Затемнение фона
        batch.setColor(0, 0, 0, 0.7f);
        batch.draw(bgPixel, 0, 0, 1080, 1920);
        batch.setColor(Color.WHITE);

        // 2. Плашка результата (в натуральный размер по центру)
        if (currentResult != null) {
            float pw = currentResult.getRegionWidth();
            float ph = currentResult.getRegionHeight();

            float px = (1080 - pw) / 2f;
            float py = (1920 - ph) / 2f + 100;

            batch.draw(currentResult, px, py, pw, ph);
        }

        // 3. Кнопка "На главную"
        if (btnHome != null) {
            batch.draw(btnHome, btnBounds.x, btnBounds.y, btnBounds.width, btnBounds.height);
        }
    }

    public void handleInput(Viewport viewport) {
        if (!visible) return;
        if (Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);
            if (btnBounds.contains(touch.x, touch.y)) {
                hide();
                // Если соединение открыто — отправляем запрос на выход
                if (game.gameClient != null && game.gameClient.isOpen()) {
                    game.gameClient.sendLeaveRoom();
                }
            }
        }
    }

    public void hide() { visible = false; }
    public boolean isVisible() { return visible; }
}
