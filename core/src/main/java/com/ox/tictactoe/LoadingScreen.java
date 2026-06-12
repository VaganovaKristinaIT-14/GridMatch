package com.ox.tictactoe;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class LoadingScreen extends ScreenAdapter {
    private final MainGame game;
    private float displayTimer = 0;
    private final float MIN_DISPLAY_TIME = 5.0f;

    private TextureRegion venokRegion;
    private TextureRegion textRegion;

    private com.badlogic.gdx.Screen nextScreen;

    private OrthographicCamera camera;
    private Viewport viewport;

    private final float WORLD_WIDTH = 1080f;
    private final float WORLD_HEIGHT = 1920f;

    // Кнопка выхода
    private TextureRegion btnExitIcon;
    private Rectangle btnExitBounds;
    private boolean showExitButton;

    public LoadingScreen(MainGame game) {
        this(game, false);
    }

    public LoadingScreen(MainGame game, boolean showExitButton) {
        this.game = game;
        this.showExitButton = showExitButton;

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();

        venokRegion = game.atlas.findRegion("load_venok");
        textRegion = game.atlas.findRegion("load_text");

        if (showExitButton) {
            btnExitIcon = game.atlas.findRegion("board_button_exit");
            btnExitBounds = new Rectangle(WORLD_WIDTH - 160, WORLD_HEIGHT - 160, 120, 120);
        }
    }

    public void setNextScreen(com.badlogic.gdx.Screen screen) {
        this.nextScreen = screen;
    }

    public com.badlogic.gdx.Screen getNextScreen() {
        return nextScreen;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        displayTimer += delta;

        viewport.apply();
        game.batch.setProjectionMatrix(camera.combined);

        game.batch.begin();

        if (game.allBackTexture != null) {
            game.batch.draw(game.allBackTexture, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        }

        if (venokRegion != null) {
            float w = venokRegion.getRegionWidth();
            float h = venokRegion.getRegionHeight();
            game.batch.draw(venokRegion, (WORLD_WIDTH - w) / 2f, (WORLD_HEIGHT - h) / 2f, w, h);
        }

        if (textRegion != null) {
            float w = textRegion.getRegionWidth();
            float h = textRegion.getRegionHeight();
            game.batch.draw(textRegion, (WORLD_WIDTH - w) / 2f, (WORLD_HEIGHT - h) / 2f - 350f, w, h);
        }

        if (showExitButton && btnExitIcon != null) {
            game.batch.draw(btnExitIcon, btnExitBounds.x, btnExitBounds.y, btnExitBounds.width, btnExitBounds.height);
        }

        game.batch.end();

        // Обработка нажатия крестика
        if (showExitButton && Gdx.input.justTouched()) {
            Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touch);
            if (btnExitBounds != null && btnExitBounds.contains(touch.x, touch.y)) {
                if (game.gameClient != null && game.gameClient.isOpen()) {
                    game.gameClient.sendLeaveRoom();
                }
            }
        }

        if (displayTimer >= MIN_DISPLAY_TIME && nextScreen != null) {
            game.setScreen(nextScreen);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void dispose() {
    }
}
