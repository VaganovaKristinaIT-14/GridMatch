package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.ox.tictactoe.LoadingScreen;
import com.ox.tictactoe.MainGame;

public class MenuScreen extends ScreenAdapter {

    private final MainGame game;
    private final Stage stage;
    private Table rootTable;
    private VisLabel errorLabel;

    public MenuScreen(MainGame game) {
        this(game, null);
    }

    public MenuScreen(MainGame game, String errorMessage) {
        this.game = game;
        this.stage = new Stage(new FitViewport(1080, 1920));
        setupUI(errorMessage);
    }
    public void showError(String message) {
        if (errorLabel != null && message != null && !message.isEmpty()) {
            errorLabel.setText(message);
        }
    }

    private void setupUI(String errorMessage) {
        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.top();

        // Устанавливаем лес на фон
        if (game.allBackTexture != null) {
            rootTable.setBackground(new TextureRegionDrawable(game.allBackTexture));
        }
        stage.addActor(rootTable);

        // Стиль для текста ошибки
        VisLabel.LabelStyle errorStyle = new VisLabel.LabelStyle();
        errorStyle.font = game.mainFont != null ? game.mainFont : VisUI.getSkin().getFont("default-font");
        errorStyle.fontColor = Color.RED;

        errorLabel = new VisLabel("", errorStyle);
        errorLabel.setAlignment(Align.center);
        errorLabel.setFontScale(1.5f);
        errorLabel.setWrap(true);
        // Если передали сообщение об ошибке — показываем
        if (errorMessage != null && !errorMessage.isEmpty()) {
            errorLabel.setText(errorMessage);
        }

        // Кнопка профиля
        Image profileButton = new Image(game.atlas.findRegion("main_bt_profile"));
        profileButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ProfileScreen(game));
            }
        });

        // Заголовок "GridMatch"
        Image titleImage = new Image(game.atlas.findRegion("main_text"));

        // Главный квадрат-сетка
        Image gridSquareImage = new Image(game.atlas.findRegion("main_square"));

        // Кнопка "Создать игру"
        Image createGameButton = new Image(game.atlas.findRegion("main_button_create"));
        createGameButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LobbyScreen(game));
            }
        });

        // Кнопка "Присоединиться"
        Image joinGameButton = new Image(game.atlas.findRegion("main_button_join"));
        joinGameButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (game.gameClient != null && game.gameClient.isOpen()) {
                    game.gameClient.sendJoinRandomLobby();
                }
                game.setScreen(new LoadingScreen(game, true));
            }
        });

        // Сборка экрана
        Table topBar = new Table();
        topBar.add(profileButton).padTop(60f).padLeft(0f).left().expandX();

        rootTable.add(topBar).fillX().row();

        // Ошибка (красный текст сверху, как в LoginScreen)
        rootTable.add(errorLabel).width(900f).height(180f).padTop(15f).padBottom(15f).row();


        rootTable.add(titleImage).padTop(30f).row();
        rootTable.add(gridSquareImage).size(901f, 890f).padTop(70f).row();
        rootTable.add(createGameButton).size(737f, 210f).padTop(20f).row();
        rootTable.add(joinGameButton).size(737f, 210f).padTop(20f).row();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!game.isInputBlocked()) {
            stage.act(delta);
        }
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

}
