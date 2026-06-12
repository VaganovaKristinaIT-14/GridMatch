package com.ox.tictactoe;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;
import com.ox.tictactoe.screens.BoardScreen;
import com.ox.tictactoe.screens.ConnectionLostOverlay;
import com.ox.tictactoe.screens.LobbyScreen;
import com.ox.tictactoe.screens.LoginScreen;
import com.ox.tictactoe.screens.MenuScreen;
import com.ox.tictactoe.screens.ReconnectingOverlay;
import com.ox.tictactoe.screens.minigames.MiniGameInterface;


// импорты для серерной игры
import java.net.URI;
import java.net.URISyntaxException;

/** Точка входа и инициализация подключения. Хранит общие ресурсы (шрифты, атласы, клиент сети)
 * Он единственный, кто имеет право переключать экраны */
public class MainGame extends Game {

    // === ГЛОБАЛЬНАЯ ОБРАБОТКА РАЗРЫВА СОЕДИНЕНИЯ ===
    public volatile boolean isDisconnected = false;
    private ConnectionLostOverlay connectionLostOverlay;

    // TODO true - экран логина, false - сразу игра
    public static final boolean AUTH_ENABLED = true;
    public Skin skin;   // UI Kit в коде(это для атласа)
    public SpriteBatch batch; // Для отрисовки всех элементов (в классах игр не надо его каждый раз прописывать)

    //Атлас должен быть публичным!! В мини играх просто обращаемся к нему
    public TextureAtlas atlas;

    // ссылка на клиента, чтобы потом отправлять ходы
    public GameClient gameClient;
    public com.badlogic.gdx.graphics.g2d.BitmapFont font;
    public int turnTimeoutSeconds = 180; // таймаут бездействия в секундах

    //Список всех игроков и личный ID (идентификатор игрока, которому принадлежит объект класса)
    public com.badlogic.gdx.utils.Array<PlayerModel> players = new com.badlogic.gdx.utils.Array<>();
    public int myResourceId = -1; // Твой ID, полученный от сервера
    public long userId = -1;      // ID из базы данных (получен при логине)
    public String myUsername = "Гость"; // Твое имя после логина
    // Список всех игроков и личный ID
    public int currentActivePlayerId = -1; // Актуальный ID ходящего (для синхронизации)
    public com.ox.tictactoe.screens.BoardScreen boardScreen; // Прямая ссылка на доску

    public BitmapFont mainFont; //Основной шрифт

    //Размер доски
    public int currentBoardSize = 3; // Заглушка, позже будет приходить из лобби
    ReconnectingOverlay reconnectingOverlay;
    boolean isReconnecting = false;
    float reconnectTimer = 0;
    private String savedUsername = null;
    private String savedPassword = null;

    public TextureRegion allBackTexture;     // Теперь это Region
    private Texture _rawAllBack;             // Сами текстуры прячем, чтобы потом очистить память
    public String currentRoomId = null;
    @Override
    public void create() {
        // 1. Инициализация UI-библиотеки
        if (!VisUI.isLoaded()) VisUI.load();

        // 2. Инструменты отрисовки
        batch = new SpriteBatch();

        // 3. Загрузка шрифта
        font = VisUI.getSkin().getFont("default-font");
        font.getData().markupEnabled = true;
        /*
        // старый код
        // Настройка генератора
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pastry_chef.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        parameter.size = 45; // Размер шрифта
        parameter.color = com.badlogic.gdx.graphics.Color.WHITE;
        // Важно: добавляем символы, которые будем использовать
        parameter.characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!?.()[],:;+-= " +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя";
>
        mainFont = generator.generateFont(parameter);
        generator.dispose(); // Очищаем генератор, сам шрифт остается в mainFont
        */
        // Настройка генератора
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pastry_chef.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        // --- ИЗМЕНЕНИЯ ЗДЕСЬ (Увеличили размер и добавили символы для паролей) ---
        parameter.size = 50; // Чуть увеличили размер
        parameter.color = com.badlogic.gdx.graphics.Color.WHITE;
        parameter.characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!?.()[],:;+-= " +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя*"; // Добавили звездочку для пароля!

        mainFont = generator.generateFont(parameter);
        generator.dispose(); // Очищаем генератор, сам шрифт остается в mainFont

        atlas = new TextureAtlas(Gdx.files.internal("uiskin.atlas"));

        // ЗАГРУЗКА И КОНВЕРТАЦИЯ
        try {
            // Сначала грузим саму текстуру
            _rawAllBack = new Texture(Gdx.files.internal("all_back.png"));

            // Включаем фильтрацию
            _rawAllBack.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

            // СРАЗУ ПРЕВРАЩАЕМ В REGION, чтобы не было ошибок типа (как на скрине)
            allBackTexture = new TextureRegion(_rawAllBack);

            System.out.println("[CORE] Фоны загружены и обернуты в TextureRegion");
        } catch (Exception e) {
            System.err.println("[CORE] ОШИБКА ПРИ ЗАГРУЗКЕ PNG: " + e.getMessage());
        }

        // Инициализируем наш глобальный оверлей
        connectionLostOverlay = new ConnectionLostOverlay(this);
        reconnectingOverlay = new ReconnectingOverlay(this);

        // 5. Соединение
        connectToServer();
        /*
        // старый код
        // 6. Стартовый экран
        if (AUTH_ENABLED) {
            // Если авторизация нужна — идем на экран логина
            setScreen(new com.ox.tictactoe.screens.LoginScreen(this));
        } else {
            // Если выключена — сразу экран загрузки (или доски),
            // но так как BoardScreen требует данных от сервера,
            // мы ставим LoadingScreen и ждем сигнала OPPONENT_FOUND в GameClient
            setScreen(new LoadingScreen(this));
        }

        */
        // 6. Стартовый экран
        if (AUTH_ENABLED) {
            // --- ИЗМЕНЕНИЕ: Строго открываем логин, чтобы он не пропадал ---
            setScreen(new com.ox.tictactoe.screens.LoginScreen(this));
        } else {
            // Если выключена — ждем сигнала OPPONENT_FOUND в GameClient
            System.out.println("[CORE] Авторизация отключена. Показываем меню...");
            setScreen(new MenuScreen(this));
        }
    }

    // Подключение к серверу.
    private void connectToServer() {
        try {
            String serverAddress = "ws://localhost:8080/game";
            // ws://46.8.237.189:8080/game
            // ws://localhost:8080/game

            // Передаем this (MainGame), чтобы GameClient мог менять экраны
            gameClient = new GameClient(new URI(serverAddress), this);

            // НОВОЕ: НАСТРОЙКА ПИНГА (HEARTBEAT)
            // Если пинг до сервера не проходит в течение 3 секунд,
            // клиент автоматически признает сеть потерянной и вызовет onClose()
            gameClient.setConnectionLostTimeout(3);

            gameClient.connect(); // Эта команда запускает подключение в фоновом потоке

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // === ГЛОБАЛЬНЫЙ RENDER: РИСУЕТ ОВЕРЛЕЙ ПОВЕРХ ВСЕГО ===
    @Override
    public void render() {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R)) {
            if (gameClient != null) {
                gameClient.hardClose();
            }
        }
        // Если соединение потеряно, блокируем обновление текущего экрана,
        // но оставляем его отрисовку для заднего фона
        float delta = Gdx.graphics.getDeltaTime();
        if (isReconnecting) {
            reconnectTimer += delta;
            reconnectingOverlay.update(delta);
            if (reconnectTimer >= 60f) {
                isReconnecting = false;
                isDisconnected = true;
                setInputBlocked(false);
                reconnectingOverlay.hide();
                // connectionLostOverlay отобразится сам при isDisconnected == true
                return;
            }
            // 1. Рисуем текущий экран (доску, меню и т.д.)
            super.render();

            // 2. Сохраняем текущую проекцию batch
            com.badlogic.gdx.math.Matrix4 oldMatrix = batch.getProjectionMatrix().cpy();

            // 3. Устанавливаем фиксированную проекцию 1080×1920
            batch.getProjectionMatrix().setToOrtho2D(0, 0, 1080, 1920);

            // 4. Рисуем оверлей
            batch.begin();
            reconnectingOverlay.draw(batch, mainFont);
            batch.end();

            // 5. Восстанавливаем старую проекцию (на случай, если текущий экран её изменит в следующем кадре)
            batch.setProjectionMatrix(oldMatrix);
            return;
        }

        if (isDisconnected) {
            // Очищаем экран (на случай артефактов)
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

            // Если экран существует, просим его только нарисоваться (без логики инпута)
            if (screen != null) {
                // Хитрость: мы не вызываем screen.render(), чтобы не работал инпут экранов,
                // мы просто рисуем оверлей. Но чтобы фон не был черным,
                // вызываем стандартный рендер, но перехватываем клики внутри оверлея
                super.render();
            }
            // Рисуем оверлей потери соединения ПОВЕРХ всего
            connectionLostOverlay.render();
        } else {
            // Нормальная работа игры
            super.render();
        }
    }

    // === ГЛОБАЛЬНЫЙ RESIZE ===
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (connectionLostOverlay != null) {
            connectionLostOverlay.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        // 1. Закрываем сетевое соединение
        if (gameClient != null) {
            System.out.println("[CORE] Закрытие сетевого клиента...");
            gameClient.close();
        }

        // 2. Освобождаем ресурсы
        if (batch != null) batch.dispose();
        if (atlas != null) atlas.dispose();
        if (_rawAllBack != null) _rawAllBack.dispose();
        if (VisUI.isLoaded()) VisUI.dispose();
        if (mainFont != null) mainFont.dispose();

        // 3. Гарантируем выход из JVM (опционально, но помогает с Gradle)
        // System.exit(0);
    }
    private InputProcessor emptyInputProcessor = new InputProcessor() {
        @Override public boolean keyDown(int keycode) { return false; }
        @Override public boolean keyUp(int keycode) { return false; }
        @Override public boolean keyTyped(char character) { return false; }
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }

        @Override
        public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {return false;}

        @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
        @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
        @Override public boolean scrolled(float amountX, float amountY) { return false; }
    };
    public void setInputBlocked(boolean blocked) {
        if (blocked) {
            Gdx.input.setInputProcessor(emptyInputProcessor);
        } else {
            // Восстанавливаем процессор текущего экрана (вызов show() переустановит свой процессор)
            if (screen != null) {
                screen.show(); // перевызов show сбросит процессор
            }
        }
    }
    public boolean isInputBlocked() {
        return isReconnecting;
    }
    public void saveCredentials(String username, String password) {
        this.savedUsername = username;
        this.savedPassword = password;
        System.out.println("[CORE] Сохранены учётные данные для " + username);
    }

    public void clearCredentials() {
        this.savedUsername = null;
        this.savedPassword = null;
        System.out.println("[CORE] Учётные данные очищены");
    }

    public String getSavedUsername() {
        return savedUsername;
    }

    public String getSavedPassword() {
        return savedPassword;
    }

    public boolean hasSavedCredentials() {
        return savedUsername != null && savedPassword != null;
    }
}
