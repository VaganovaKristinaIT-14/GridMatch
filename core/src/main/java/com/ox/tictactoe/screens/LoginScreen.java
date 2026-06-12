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
import com.kotcrab.vis.ui.widget.VisTextField;
import com.ox.tictactoe.MainGame;

public class LoginScreen extends ScreenAdapter {
    private Table rootTable;
    private final MainGame game;
    private final Stage stage;

    private TextureRegionDrawable regBtnDrawable, loginBtnDrawable, enterBtnDrawable, registerBtnDrawable;
    private Image submitButton, imgRegisterButton, imgEnterButton;
    private VisLabel titleLabel;
    private VisTextField loginField, passwordField, repeatPasswordField;
    private VisLabel toggleModeLabel, errorLabel;
    private Table toggleTable;


    private Table repassGroup;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell repassCell;
    private boolean isRegisterMode = true;

    public LoginScreen(MainGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(1080, 1920));
        loadTextures();
        setupUI();
    }

    private void loadTextures() {
        // Загружаем только кнопки, так как фоны берем прямо в методах
        regBtnDrawable = new TextureRegionDrawable(game.atlas.findRegion("reg_button_reg"));
        loginBtnDrawable = new TextureRegionDrawable(game.atlas.findRegion("reg_button_entr"));

        enterBtnDrawable = new TextureRegionDrawable(game.atlas.findRegion("reg_button_entr"));
        registerBtnDrawable = new TextureRegionDrawable(game.atlas.findRegion("entr_button_reg"));
    }

    private void setupUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);

        // Устанавливаем лес на фон
        if (game.allBackTexture != null) {
            rootTable.setBackground(new TextureRegionDrawable(game.allBackTexture));
        }
        stage.addActor(rootTable);

        // --- СТИЛЬ ДЛЯ ПОЛЕЙ ВВОДА ---
        Color textColor = new Color(0.9137f, 0.9451f, 0.8274f,1f);

        VisTextField.VisTextFieldStyle fieldStyle = new VisTextField.VisTextFieldStyle(VisUI.getSkin().get(VisTextField.VisTextFieldStyle.class));
        if (game.mainFont != null) fieldStyle.font = game.mainFont;
        fieldStyle.fontColor = textColor;
        fieldStyle.background = null; // Делаем прозрачным, чтобы видеть неоновую рамку

        // ==========================================================
        // НАСТРОЙКА ПОЛЕЙ (Двигаем неоновую рамку по деревянной доске)
        // ==========================================================

        // 1. ЛОГИН
        loginField = new VisTextField("", fieldStyle);
        loginField.setAlignment(Align.center);

        // Цифры (45f, 220f) -> двигают зеленую рамку ВВЕРХ и ВПРАВО по деревяшке
        Table loginGroup = createWoodBoard("reg_log_wood", loginField, 45f, 220f);

        // 2. ПАРОЛЬ
        passwordField = new VisTextField("", fieldStyle);
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        passwordField.setAlignment(Align.center);
        // Доска пароля шире, поэтому сдвигаем неон сильнее вправо (280f)
        Table passGroup = createWoodBoard("reg_pass_wood", passwordField, 60f, 280f);

        // 3. ПОВТОР ПАРОЛЯ
        repeatPasswordField = new VisTextField("", fieldStyle);
        repeatPasswordField.setPasswordMode(true);
        repeatPasswordField.setPasswordCharacter('*');
        repeatPasswordField.setAlignment(Align.center);
        repassGroup = createWoodBoard("reg_repass_wood", repeatPasswordField, 50f, 250f);

        // --- НАДПИСИ И КНОПКИ ---
        VisLabel.LabelStyle labelStyle = new VisLabel.LabelStyle();
        labelStyle.font = game.mainFont != null ? game.mainFont : VisUI.getSkin().getFont("default-font");
        labelStyle.fontColor = textColor;

        titleLabel = new VisLabel("Создать аккаунт", labelStyle);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(2.5f); // Крупный заголовок

        imgEnterButton = new Image(enterBtnDrawable);
        imgEnterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) { toggleMode(); }
        });

        imgRegisterButton = new Image(registerBtnDrawable);
        imgRegisterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) { toggleMode(); }
        });

        toggleTable = new Table();
        toggleModeLabel = new VisLabel("Уже есть аккаунт?", labelStyle);
        toggleModeLabel.setFontScale(1.4f);

        errorLabel = new VisLabel("", labelStyle);
        errorLabel.setAlignment(Align.center);
        errorLabel.setFontScale(1.3f);
        errorLabel.setWrap(true);

        submitButton = new Image(regBtnDrawable);
        submitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) { handleSubmit(); }
        });

        // ==========================================================
        // СБОРКА ЭКРАНА (Расставляем деревяшки по экрану)
        // ==========================================================
        rootTable.add(errorLabel).width(900f).height(220f).padTop(15f).row();

        // size - оригинальный размер деревяшек из твоего атласа
        rootTable.add(loginGroup).size(879, 323).padBottom(20).row();
        rootTable.add(passGroup).size(936, 394).padBottom(20).row();

        // Сохраняем ячейку 3-го поля для переключения режимов
        repassCell = rootTable.add(repassGroup).size(890, 324).padBottom(20);
        repassCell.row();

        rootTable.add(submitButton).padBottom(40).row();
        rootTable.add(toggleTable).row();

        updateUIVisibility();
    }

    /**
     * Создает деревянную доску, накладывает на нее неоновую рамку и поле ввода.
     * @param padB Сдвиг неоновой рамки ВВЕРХ от низа деревяшки
     * @param padL Сдвиг неоновой рамки ВПРАВО от левого края деревяшки
     */
    private Table createWoodBoard(String woodRegionName, VisTextField field, float padB, float padL) {
        Table wrapper = new Table();

        // 1. Фон: Деревянная доска
        if (game.atlas.findRegion(woodRegionName) != null) {
            wrapper.setBackground(new TextureRegionDrawable(game.atlas.findRegion(woodRegionName)));
        }

        // 2. Стопка: Объединяем Неоновый прямоугольник и Поле ввода
        com.badlogic.gdx.scenes.scene2d.ui.Stack neonStack = new com.badlogic.gdx.scenes.scene2d.ui.Stack();

        // 2.1 Неон
        if (game.atlas.findRegion("reg_entr_window") != null) {
            neonStack.add(new Image(game.atlas.findRegion("reg_entr_window")));
        }

        // 2.2 Поле ввода (вписываем текст ровно в размеры неона: 597x179)
        Table textTable = new Table();
        textTable.add(field).width(500).height(95).padBottom(15f); // padBottom центрирует текст внутри неона
        neonStack.add(textTable);

        // 3. Кладем неон на деревяшку.
        // Размеры 597x179 - это точный размер reg_entr_window в твоем атласе.
        // Выравниваем по левому нижнему углу (bottom().left()) и сдвигаем через padBottom(padB) и padLeft(padL)
        wrapper.add(neonStack).width(597).height(179).expand().bottom().left().padBottom(padB).padLeft(padL);

        return wrapper;
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        errorLabel.setText("");
        updateUIVisibility();
    }

    private void updateUIVisibility() {
        toggleTable.clear();

        if (isRegisterMode) {
            titleLabel.setText("Создать аккаунт");
            submitButton.setDrawable(regBtnDrawable);
            if (repassCell != null) {
                repassCell.height(324).padBottom(20);
            }
            repassGroup.setVisible(true);

            toggleModeLabel.setText("Уже есть аккаунт?");
            toggleTable.add(toggleModeLabel).padRight(20);
            toggleTable.add(imgEnterButton).height(100).width(200).padTop(50);
        } else {
            titleLabel.setText("Войти в аккаунт");
            submitButton.setDrawable(loginBtnDrawable);
            if (repassCell != null) {
                repassCell.height(0).padBottom(0);
            }
            repassGroup.setVisible(false);

            toggleModeLabel.setText("Нет аккаунта?");
            toggleTable.add(toggleModeLabel).padRight(20);
            toggleTable.add(imgRegisterButton).height(100).width(400).padTop(60);
        }

        if (rootTable != null) {
            rootTable.invalidateHierarchy();
        }
    }

    private void handleSubmit() {
        String u = loginField.getText();
        String p = passwordField.getText();

        if (u.isEmpty() || p.isEmpty()) {
            showError("Заполните все поля!");
            return;
        }

        if (game.gameClient == null || !game.gameClient.isOpen()) {
            showError("Нет связи с сервером!");
            return;
        }

        if (isRegisterMode) {
            if (!p.equals(repeatPasswordField.getText())) {
                showError("Пароли не совпадают!");
                return;
            }
            game.gameClient.sendRegister(u, p);
        } else {
            game.gameClient.sendLogin(u, p);
        }
    }

    public void showError(String msg) { errorLabel.setText(msg); }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    @Override public void hide() { Gdx.input.setInputProcessor(null); }
    @Override public void dispose() { stage.dispose(); }
}
