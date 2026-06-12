package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.ox.tictactoe.MainGame;

public class ProfileScreen extends ScreenAdapter {

    private final MainGame game;
    private final Stage stage;
    private Table rootTable;

    public ProfileScreen(MainGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(1080, 1920));
        setupUI();
    }

    private void setupUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);

        if (game.allBackTexture != null) {
            rootTable.setBackground(new TextureRegionDrawable(game.allBackTexture));
        }
        stage.addActor(rootTable);

        // Инициализация графики из атласа
        Image exitButton = new Image(game.atlas.findRegion("profile_button_exit"));
        Image avatarImage = new Image(game.atlas.findRegion("profile_avatar"));
        Image nameLabelImage = new Image(game.atlas.findRegion("profile_text_name"));

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        // ==========================================================
        // ТАБЛИЧКА И ПОЛЕ ВВОДА (Stack: Картинка снизу, текст сверху)
        // ==========================================================

        Stack inputStack = new Stack();

        // 1. Слой подложки (Сама табличка)
        Image tabletBackground = new Image(game.atlas.findRegion("profile_window"));
        inputStack.add(tabletBackground);

        // 2. Настраиваем поле имени
        Label.LabelStyle tfStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        if (game.mainFont != null) tfStyle.font = game.mainFont;
        tfStyle.fontColor = Color.WHITE;
        tfStyle.background = null; // Делаем родную подложку поля АБСОЛЮТНО ПРОЗРАЧНОЙ

        // Берем имя из переменной, которую сохранили при логине
        String playerName = game.myUsername;

        // Если по какой-то причине оно пустое (например, зашли без интернета)
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Игрок 1";
        }

        VisLabel nameStatic = new VisLabel(playerName, tfStyle);
        nameStatic.setAlignment(Align.center);

        // 3. Слой текста
        Table textTable = new Table();
        // Растягиваем текстовое поле на весь размер таблички.
        // padBottom можно чуть изменить (например, 10f), если текст визуально не по центру по вертикали
        textTable.add(nameStatic).expand().fill().padBottom(15f);
        inputStack.add(textTable);


        // ==========================================================
        // СБОРКА ЭКРАНА
        // ==========================================================

        Stack mainStack = new Stack();

        // --- СЛОЙ 1: ДЕРЕВЯННАЯ ДОСКА И ЕЁ СОДЕРЖИМОЕ ---
        Table boardTable = new Table();
        boardTable.setBackground(new TextureRegionDrawable(game.atlas.findRegion("profile_wood_board")));
        boardTable.top(); // Элементы идут строго сверху вниз

        boardTable.add(avatarImage).size(413f, 412f).padTop(170f).row();
        boardTable.add(nameLabelImage).size(136f, 60f).padTop(40f).row();

        // Вставляем наш Stack (табличка + текст) сразу под слово "Имя".
        // Размеры 600x180 подобраны пропорционально скриншоту (можно подкорректировать)
        boardTable.add(inputStack).size(600f, 180f).padTop(20f).row();


        // --- СЛОЙ 3: КНОПКА КРЕСТИКА ---
        Table exitTable = new Table();
        exitTable.top().right();
        exitTable.add(exitButton).size(193f, 190f).padTop(-35f).padRight(-50f);


        // --- ДОБАВЛЯЕМ СЛОИ В STACK ---
        mainStack.add(boardTable);
        mainStack.add(exitTable);

        // Размещаем по центру главного экрана
        rootTable.add(mainStack).size(948f, 1498f).center();
    }

    @Override
    public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (!game.isInputBlocked()) {
            stage.act(delta);
        }
        stage.draw();
    }

    @Override public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    @Override public void hide() { Gdx.input.setInputProcessor(null); }
    @Override public void dispose() { stage.dispose(); }
}
