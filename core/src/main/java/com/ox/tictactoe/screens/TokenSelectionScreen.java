package com.ox.tictactoe.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.ox.tictactoe.MainGame;
import java.util.HashMap;
import java.util.Map;

public class TokenSelectionScreen extends ScreenAdapter {
    private final MainGame game;
    private final OrthographicCamera camera;
    private final Viewport viewport;

    // Константы твоего разрешения из BoardScreen
    private final float WORLD_WIDTH = 1080f;
    private final float WORLD_HEIGHT = 1920f;

    private TextureRegion background, iconBack, btnYes, btnNo;
    private final Map<String, TextureRegion> tokenRegions = new HashMap<>();
    private final String[] tokenNames = {"sun", "tree", "key", "crown", "wolf", "bird"};

    private String selectedToken = null;
    private String confirmedToken = null;
    private final Map<Integer, String> otherPlayersSelections = new HashMap<>();

    public TokenSelectionScreen(MainGame game) {
        this.game = game;

        // Настройка камеры как в BoardScreen, чтобы координаты кликов и отрисовки совпадали
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();

        loadAssets();
    }

    private void loadAssets() {
        background = game.allBackTexture;
        iconBack = game.atlas.findRegion("icon_back");
        btnYes = game.atlas.findRegion("icon_yes");
        btnNo = game.atlas.findRegion("icon_no");

        for (String name : tokenNames) {
            TextureRegion region = game.atlas.findRegion("icon_" + name);
            if (region != null) tokenRegions.put(name.toUpperCase(), region);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        if (background != null) {
            game.batch.draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        }

        drawSmart(iconBack, Color.WHITE);

        for (String tokenKey : tokenNames) {
            String key = tokenKey.trim().toUpperCase();
            TextureRegion region = tokenRegions.get(key);
            if (region == null) continue;

            // ПОРЯДОК ПРОВЕРКИ ВАЖЕН:
            if (key.equals(confirmedToken)) {
                game.batch.setColor(Color.GREEN); // Мой выбор подтвержден
            }
            else if (otherPlayersSelections.containsValue(key)) {
                game.batch.setColor(Color.RED);   // ЗАНЯТО другим игроком (Красный)
            }
            else if (key.equals(selectedToken)) {
                game.batch.setColor(Color.CYAN);  // Предпросмотр (Циан)
            }
            else {
                game.batch.setColor(Color.WHITE); // Свободно
            }

            drawSmart(region, game.batch.getColor());
        }

        game.batch.setColor(Color.WHITE);
        drawSmart(btnYes, Color.WHITE);
        drawSmart(btnNo, Color.WHITE);

        game.batch.end();
        handleInput();
    }

    /**
     * РИСУЕТ ТАК, ЧТОБЫ КАРТИНКА ВСТАЛА НА СВОЕ МЕСТО ИЗ ФИГМЫ
     * Используем смещения (offset), которые LibGDX зашивает в AtlasRegion
     */
    private void drawSmart(TextureRegion region, Color color) {
        if (region == null) return;

        if (region instanceof TextureAtlas.AtlasRegion) {
            TextureAtlas.AtlasRegion atlasRegion = (TextureAtlas.AtlasRegion) region;
            game.batch.setColor(color);
            // draw(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcW, srcH, flipX, flipY)
            game.batch.draw(
                atlasRegion.getTexture(),
                atlasRegion.offsetX,            // Смещение по X из Фигмы
                atlasRegion.offsetY,            // Смещение по Y из Фигмы
                0, 0,                           // Origin
                atlasRegion.packedWidth,        // Реальная ширина иконки
                atlasRegion.packedHeight,       // Реальная высота иконки
                1, 1,                           // Scale
                0,                              // Rotation
                atlasRegion.getRegionX(),       // Координаты в атласе
                atlasRegion.getRegionY(),
                atlasRegion.getRegionWidth(),
                atlasRegion.getRegionHeight(),
                false, false                    // Flips
            );
        } else {
            game.batch.draw(region, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        }
    }

    /**
     * ИСПРАВЛЕННЫЙ КЛИК: теперь он попадает точно в иконку, даже если вокруг неё пустота
     */
    private boolean isTouched(TextureRegion region, float tx, float ty) {
        if (!(region instanceof TextureAtlas.AtlasRegion)) return false;
        TextureAtlas.AtlasRegion ar = (TextureAtlas.AtlasRegion) region;

        // Проверяем, попадает ли клик в область "видимой" картинки с учетом её смещения
        return tx >= ar.offsetX && tx <= ar.offsetX + ar.packedWidth &&
            ty >= ar.offsetY && ty <= ar.offsetY + ar.packedHeight;
    }
    private void handleInput() {
        if (game.isInputBlocked()) return;
        if (!Gdx.input.justTouched()) return;

        Vector3 touch = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touch);

        // 1. Кнопка "ОТМЕНА" (btnNo) должна работать ВСЕГДА,
        // даже если выбор уже подтвержден (confirmedToken != null)
        if (isTouched(btnNo, touch.x, touch.y)) {
            // Всегда отправляем запрос на отмену (сервер сам разберётся)
            game.gameClient.sendDeselectToken();

            // Немедленно удаляем нашу фишку из локального списка занятых (если она там есть)
            if (confirmedToken != null) {
                otherPlayersSelections.values().remove(confirmedToken);
            }

            confirmedToken = null;
            selectedToken = null;
            return;
        }

        // 2. Если выбор уже окончательно подтвержден и отправлен,
        // блокируем остальные клики (кроме кнопки No, которая выше)
        if (confirmedToken != null) return;

        // 3. Кнопка "ПОДТВЕРДИТЬ" (btnYes)
        if (isTouched(btnYes, touch.x, touch.y)) {
            if (selectedToken != null) {
                // Проверка на всякий случай перед отправкой: не занял ли кто-то, пока мы думали
                if (!otherPlayersSelections.containsValue(selectedToken)) {
                    confirmedToken = selectedToken;
                    sendTokenToServer(confirmedToken);
                } else {
                    selectedToken = null; // Фишку уже увели
                    Gdx.app.log("DEBUG", "Не удалось подтвердить: фишка уже занята");
                }
            }
            return;
        }

        // 4. Выбор фишки для предпросмотра
        for (String name : tokenNames) {
            String key = name.toUpperCase();
            if (isTouched(tokenRegions.get(key), touch.x, touch.y)) {
                // Проверяем, не занята ли эта фишка другими
                if (!otherPlayersSelections.containsValue(key)) {
                    selectedToken = key;
                    Gdx.app.log("DEBUG", "Выбрано для предпросмотра: " + key);
                }
                break;
            }
        }
    }


    public void handleNetworkMessage(JsonValue json) {
        String type = json.getString("type", "");
        JsonValue data = json.get("data");
        if (data == null) return;

        // 1. Обработка ошибки: Фишка занята
        if (type.equals("TOKEN_UNAVAILABLE")) {
            String takenToken = data.has("selectedToken") ? data.getString("selectedToken") : null;
            if (takenToken != null && !otherPlayersSelections.containsValue(takenToken)) {
                // Добавляем занятую фишку в карту (как будто её выбрал неизвестный игрок)
                otherPlayersSelections.put(-1, takenToken);
            }
            this.confirmedToken = null;
            this.selectedToken = null;
            System.out.println("DEBUG: Выбор сброшен из-за TOKEN_UNAVAILABLE");
            return;
        }

        // 2. Обновление списка выборов
        if (type.equals("TOKEN_UPDATE") || type.equals("TOKEN_SELECTIONS") || type.equals("TOKEN_TIMEOUT") || type.equals("TOKEN_SELECTION_COMPLETE")) {
            JsonValue selections = data.get("tokenSelections");
            if (selections != null) {
                otherPlayersSelections.clear();
                boolean foundMyself = false;

                for (JsonValue entry = selections.child(); entry != null; entry = entry.next()) {
                    try {
                        int pId = Integer.parseInt(entry.name());
                        String val = entry.asString().toUpperCase();

                        if (pId != game.myResourceId) {
                            otherPlayersSelections.put(pId, val);
                            System.out.println("DEBUG: Обновление: Игрок ID " + pId + " забронировал " + val);
                        } else {
                            foundMyself = true;  // <-- МЫ ЕСТЬ В СПИСКЕ
                            this.confirmedToken = val;
                        }
                    } catch (Exception e) {
                        Gdx.app.log("ERROR", "Ошибка парсинга токена: " + e.getMessage());
                    }
                }

                // Если себя не нашли – значит, сервер сбросил наш выбор
                if (!foundMyself) {
                    this.confirmedToken = null;
                    System.out.println("DEBUG: Меня нет в списке tokenSelections, выбор сброшен");
                }
            }
        }
    }


    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    // Методы обратной связи от сервера (оставляем без изменений)
    public void updateTokensFromServer(JsonValue data) {
        if (data.has("tokenSelections")) {
            JsonValue selections = data.get("tokenSelections");
            otherPlayersSelections.clear();
            for (JsonValue entry : selections) {
                int pId = Integer.parseInt(entry.name);
                if (pId != game.myResourceId) otherPlayersSelections.put(pId, entry.asString());
            }
        }
    }

    private void sendTokenToServer(String token) {
        // ВАЖНО: тип должен быть TOKEN_SELECTION, чтобы сервер его распознал
        String json = String.format(
            "{\"type\":\"TOKEN_SELECTION\", \"data\":{\"selectedToken\":\"%s\"}}",
            token.toUpperCase()
        );
        game.gameClient.send(json);
    }
    public void restoreState(JsonValue data) {
        // Восстановление доступных токенов (необязательно для UI, но полезно)
        JsonValue available = data.get("availableTokens");
        if (available != null) {
            // можно обновить список доступных, но в текущей реализации не используется для отображения
        }

        // Восстановление выборов других игроков
        JsonValue selections = data.get("tokenSelections");
        if (selections != null) {
            otherPlayersSelections.clear();
            for (JsonValue entry = selections.child(); entry != null; entry = entry.next()) {
                int pId = Integer.parseInt(entry.name());
                String token = entry.asString().toUpperCase();
                if (pId != game.myResourceId) {
                    otherPlayersSelections.put(pId, token);
                } else {
                    this.confirmedToken = token;
                }
            }
        }

        // Сброс предпросмотра
        this.selectedToken = null;
    }
}
