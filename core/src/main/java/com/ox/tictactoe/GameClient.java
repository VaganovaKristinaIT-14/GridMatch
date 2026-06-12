package com.ox.tictactoe;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.net.URI;

import com.ox.tictactoe.screens.BoardResultOverlay;
import com.ox.tictactoe.screens.BoardScreen;
import com.ox.tictactoe.screens.MenuScreen;
import com.ox.tictactoe.screens.TokenSelectionScreen;
import com.ox.tictactoe.screens.minigames.MiniGameInterface;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/** Сетевой мост. Работает в отдельном потоке.
 * Его задача — быстро распарсить JSON от сервера и передать его в текущий активный экран
 */
public class GameClient extends WebSocketClient {


    // Ссылка на главную игру для доступа к экранам
    private final MainGame mainGame;
    private boolean reconnectAttempted = false;
    private String pendingUsername = null;
    private String pendingPassword = null;

    public GameClient(URI serverUri, MainGame mainGame) {
        super(serverUri);
        this.mainGame = mainGame;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[КЛИЕНТ] Подключено к серверу!");
        if (!MainGame.AUTH_ENABLED) {
            Map<String, Object> quickStart = new HashMap<>();
            // ПЕРЕДАЕМ СТРОКУ, А НЕ ENUM
            quickStart.put("type", "QUICK_START");
            sendJson(quickStart);
            System.out.println("[КЛИЕНТ] Отправлен запрос QUICK_START");
        }
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[КЛИЕНТ] Получено сообщение: " + message);

        try {
            JsonReader jsonReader = new JsonReader();
            final JsonValue json = jsonReader.parse(message);
            String type = json.getString("type");

            // 1. АВТОРИЗАЦИЯ
            if (type.equals("AUTH_SUCCESS")) {
                JsonValue data = json.get("data");
                mainGame.userId = data.getLong("userId", -1);
                mainGame.myUsername = data.getString("username", "Игрок");

                if (pendingUsername != null) {
                    // Обычный логин (пользователь ввёл данные)
                    mainGame.saveCredentials(pendingUsername, pendingPassword);
                    pendingUsername = null;
                    pendingPassword = null;
                    Gdx.app.postRunnable(() -> {
                        mainGame.setScreen(new MenuScreen(mainGame));
                    });
                } else {
                    // Автоматический реконнект (без комнаты)
                    System.out.println("[КЛИЕНТ] Авто-реконнект успешен, экран не меняем");
                    Gdx.app.postRunnable(() -> {
                        mainGame.isReconnecting = false;
                        mainGame.setInputBlocked(false);
                        mainGame.reconnectingOverlay.hide();
                        mainGame.isDisconnected = false;
                    });
                }

                return;
            }

            if (type.equals("AUTH_ERROR")) {
                String errorMsg = json.get("data").getString("message", "Неизвестная ошибка");
                com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                    if (mainGame.getScreen() instanceof com.ox.tictactoe.screens.LoginScreen) {
                        ((com.ox.tictactoe.screens.LoginScreen) mainGame.getScreen()).showError(errorMsg);
                    }
                });
                return;
            }
            // 2. ВЫБОР ФИШЕК
            if (type.equals("TOKEN_SELECTION_REQUEST")) {
                JsonValue data = json.get("data");
                int myRoomId = data.getInt("yourId", -1);
                if (myRoomId != -1) {
                    mainGame.myResourceId = myRoomId;
                    System.out.println("[КЛИЕНТ] Установлен ID в комнате: " + myRoomId);
                }
                String roomId = data.getString("roomId", null);
                if (roomId != null) mainGame.currentRoomId = roomId;
                int roomPlayerId = data.getInt("yourId", -1);
                mainGame.myResourceId = roomPlayerId;
                Gdx.app.postRunnable(() -> {
                    mainGame.setScreen(new TokenSelectionScreen(mainGame));
                });
                return;
            }
            if (type.equals("TOKEN_UPDATE") || type.equals("TOKEN_SELECTIONS") || type.equals("TOKEN_TIMEOUT")) {
                Gdx.app.postRunnable(() -> {
                    if (mainGame.getScreen() instanceof TokenSelectionScreen) {
                        ((TokenSelectionScreen) mainGame.getScreen()).handleNetworkMessage(json);
                    }
                });
                // Не выходим (return), так как TOKEN_UPDATE может прийти вместе с чем-то еще
            }

            // 3. НАЧАЛО ИГРЫ (ПЕРЕХОД НА ДОСКУ)
            if (type.equals("OPPONENT_FOUND")) {
                JsonValue data = json.get("data");
                updatePlayers(json);
                int startId = json.get("data").getInt("activePlayerId", 1);
                int boardSize = json.get("data").getInt("boardSize", 3);
                int turnTimeoutSeconds = json.get("data").getInt("turnTimeoutSeconds", 180);
                mainGame.turnTimeoutSeconds = turnTimeoutSeconds;
                mainGame.currentActivePlayerId = startId; // СРАЗУ ЗАПИСЫВАЕМ
                mainGame.currentBoardSize = boardSize;
                String roomId = json.get("data").getString("roomId", null);
                int roomPlayerId = data.getInt("yourId", -1);
                mainGame.myResourceId = roomPlayerId;
                if (roomId != null) mainGame.currentRoomId = roomId;
                Gdx.app.postRunnable(() -> {
                    BoardScreen board = new BoardScreen(mainGame);
                    board.setActivePlayer(startId);
                    mainGame.setScreen(board);
                });
                return;
            }

            // 4. ОБНОВЛЕНИЕ ХОДА (TURN_UPDATE)
            if (type.equals("TURN_UPDATE")) {
                final int nextId = json.get("data").getInt("activePlayerId");
                mainGame.currentActivePlayerId = nextId; // Сохраняем в мейн
                final int turnTimeoutSeconds = json.get("data").getInt("turnTimeoutSeconds", 180);
                mainGame.turnTimeoutSeconds = turnTimeoutSeconds;
                Gdx.app.postRunnable(() -> {
                    // Обновляем доску напрямую через ссылку в MainGame
                    if (mainGame.boardScreen != null) {
                        mainGame.boardScreen.setActivePlayer(nextId);
                        System.out.println("[КЛИЕНТ] Доска обновлена: ходит " + nextId);
                    }
                });
            }

            // 5. СТАРТ МИНИ-ИГРЫ
            if (type.equals("MINIGAME_START")) {
                JsonValue data = json.get("data");
                final String gameType = data.getString("gameType", "BUTTON");
                final int row = data.getInt("row", 0);
                final int col = data.getInt("col", 0);

                Gdx.app.postRunnable(() -> {
                    // Берем доску из мейна. Если её нет (мало ли), берем текущий экран если это доска
                    com.ox.tictactoe.screens.BoardScreen board = mainGame.boardScreen;
                    if (board == null && mainGame.getScreen() instanceof com.ox.tictactoe.screens.BoardScreen) {
                        board = (com.ox.tictactoe.screens.BoardScreen) mainGame.getScreen();
                    }

                    // Создаем экран игры через универсальный метод
                    com.badlogic.gdx.Screen gameScreen = createMiniGameScreen(gameType, board, row, col);

                    if (gameScreen != null) {
                        // Инициализация интерфейса
                        if (gameScreen instanceof MiniGameInterface) {
                            MiniGameInterface mg = (MiniGameInterface) gameScreen;
                            mg.setup(mainGame.myResourceId, mainGame.players);
                            mg.handleNetworkMessage(json);
                        }

                        // Логика перехода через LoadingScreen
                        com.badlogic.gdx.Screen current = mainGame.getScreen();
                        if (current instanceof LoadingScreen) {
                            // Если мы уже на экране загрузки, просто подменяем ему цель
                            ((LoadingScreen) current).setNextScreen(gameScreen);
                        } else {
                            // Если мы на доске, создаем новый экран загрузки
                            LoadingScreen ls = new LoadingScreen(mainGame);
                            ls.setNextScreen(gameScreen);
                            mainGame.setScreen(ls);
                        }
                    } else {
                        System.err.println("[КЛИЕНТ] Неизвестный тип мини-игры: " + gameType);
                    }
                });
                return;
            }
            // РЕЗУЛЬТАТ МИНИ-ИГРЫ
            if (type.equals("MINIGAME_RESULT")) {
                if (!(mainGame.getScreen() instanceof MiniGameInterface)) {
                    System.out.println("[КЛИЕНТ] Игнорируем MINIGAME_RESULT, так как текущий экран не мини-игра");
                    return;
                }
                JsonValue data = json.get("data");
                final int winId = data.getInt("minigameWinnerId");
                final int row = data.getInt("row");
                final int col = data.getInt("col");

                Gdx.app.postRunnable(() -> {
                    // А) Обновляем состояние доски (чтобы фишка появилась)
                    if (mainGame.boardScreen != null) {
                        mainGame.boardScreen.onCellCaptured(row, col, winId);
                        System.out.println("[КЛИЕНТ] Доска: Клетка [" + row + "," + col + "] захвачена игроком " + winId);
                    }

                    // Б) Прокидываем результат в текущий экран
                    com.badlogic.gdx.Screen current = mainGame.getScreen();
                    if (current instanceof com.ox.tictactoe.screens.minigames.MiniGameInterface) {
                        ((com.ox.tictactoe.screens.minigames.MiniGameInterface) current).handleNetworkMessage(json);
                    }
                });
            }

            // 6. ВЫХОД ИГРОКА
            if (type.equals("PLAYER_LEFT")) {
                JsonValue dataObj = json.get("data");
                if (dataObj != null) {
                    int leftId = dataObj.getInt("id", -1);
                    Gdx.app.postRunnable(() -> {
                        if (mainGame.getScreen() instanceof com.ox.tictactoe.screens.BoardScreen) {
                            ((com.ox.tictactoe.screens.BoardScreen) mainGame.getScreen()).onPlayerLeft(leftId);
                        }
                        for (int i = 0; i < mainGame.players.size; i++) {
                            if (mainGame.players.get(i).id == leftId) {
                                mainGame.players.removeIndex(i);
                                break;
                            }
                        }
                    });
                }
                return;
            }

            // 7. УНИВЕРСАЛЬНЫЙ ПРОКИД (Для MINIGAME_RESULT и прочих действий внутри игр)
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                com.badlogic.gdx.Screen current = mainGame.getScreen();
                if (current instanceof MiniGameInterface) {
                    ((MiniGameInterface) current).handleNetworkMessage(json);
                } else if (current instanceof LoadingScreen) {
                    com.badlogic.gdx.Screen pending = ((LoadingScreen) current).getNextScreen();
                    if (pending instanceof MiniGameInterface) {
                        ((MiniGameInterface) pending).handleNetworkMessage(json);
                    }
                }
            });

            // 8. СОЗДАНИЕ ЛОББИ
            if (type.equals("LOBBY_CREATED")) {
                JsonValue data = json.get("data");
                String roomId = data.getString("roomId");
                mainGame.currentRoomId = roomId;
                int playerCount = data.getInt("playerCount");
                int boardSize = data.getInt("boardSize");

                System.out.println("[КЛИЕНТ] Лобби создано! RoomId: " + roomId +
                    " | Игроков: " + playerCount + " | Доска: " + boardSize + "x" + boardSize);

                // Сохраняем размер доски в MainGame
                mainGame.currentBoardSize = boardSize;

                // Переходим на экран ожидания
                Gdx.app.postRunnable(() -> {
                    LoadingScreen ls = new LoadingScreen(mainGame, true);
                    mainGame.setScreen(ls);
                });
            }
            // 9. НЕТ СВОБОДНЫХ КОМНАТ, ЧТОБЫ ПРИСОЕДИНИТЬСЯ
            if (type.equals("LOBBY_NOT_FOUND") || type.equals("LOBBY_FULL")) {
                final String errorMsg = json.get("data").getString("message", "Нет свободных комнат");
                System.out.println("[КЛИЕНТ] " + errorMsg);

                Gdx.app.postRunnable(() -> {
                    mainGame.setScreen(new com.ox.tictactoe.screens.MenuScreen(mainGame, errorMsg));
                });
            }
            if (type.equals("ALREADY_IN_GAME")) {
                final String errorMsg = json.get("data").getString("message", "Вы уже играете на другом устройстве");
                System.out.println("[КЛИЕНТ] " + errorMsg);

                Gdx.app.postRunnable(() -> {
                    mainGame.setScreen(new MenuScreen(mainGame, errorMsg));
                });
                return;
            }
            // 10. ВЫХОД ИЗ КОМНАТЫ
            if (type.equals("ROOM_LEFT")) {
                mainGame.currentRoomId = null;
                System.out.println("[КЛИЕНТ] Успешно вышел из комнаты");
                Gdx.app.postRunnable(() -> {
                    mainGame.players.clear();
                    mainGame.myResourceId = -1;
                    mainGame.currentActivePlayerId = -1;
                    mainGame.boardScreen = null;
                    mainGame.setScreen(new MenuScreen(mainGame));
                });
            }
            if (type.equals("GAME_OVER")) {
                mainGame.currentRoomId = null;
                JsonValue dataObj = json.get("data");
                if (dataObj != null) {
                    final int winnerId = dataObj.getInt("winnerId", 0);
                    final String reason = dataObj.getString("reason", "");

                    System.out.println("[КЛИЕНТ] Игра окончена! Победитель: " + winnerId + " | Причина: " + reason);

                    Gdx.app.postRunnable(() -> {
                        if (mainGame.getScreen() instanceof BoardScreen) {
                            BoardScreen board = (BoardScreen) mainGame.getScreen();
                            // Если была ничья, игнорируем повторное GAME_OVER от сервера
                            if (board.isDrawFinished) {
                                System.out.println("[КЛИЕНТ] Игнорируем GAME_OVER, так как игра уже завершена ничьей");
                                return;
                            }
                            if (winnerId == mainGame.myResourceId) {
                                board.showGameOver(BoardResultOverlay.ResultState.WIN);
                            } else {
                                board.showGameOver(BoardResultOverlay.ResultState.LOSE);
                            }
                        }
                    });
                }
                return;
            }
            if (type.equals("PLAYER_TIMEOUT")) {
                final String msg = json.get("data").getString("message", "Игрок удалён за бездействие");
                Gdx.app.postRunnable(() -> {
                    mainGame.currentRoomId = null;
                    mainGame.setScreen(new MenuScreen(mainGame, msg));
                });

                return;
            }
            if (type.equals("TOKEN_UNAVAILABLE")) {
                JsonValue data = json.get("data");
                String takenToken = data.has("selectedToken") ? data.getString("selectedToken") : null;
                if (takenToken != null) {
                    // Если мы в TokenSelectionScreen – otherPlayersSelections обновится через handleNetworkMessage
                    // Здесь просто логируем
                    System.out.println("КЛИЕНТ: TOKEN_UNAVAILABLE, фишка занята: " + takenToken);
                }
                // Прокидываем в текущий экран (TokenSelectionScreen)
                Gdx.app.postRunnable(() -> {
                    if (mainGame.getScreen() instanceof TokenSelectionScreen) {
                        ((TokenSelectionScreen) mainGame.getScreen()).handleNetworkMessage(json);
                    }
                });
                return;
            }

            if (type.equals("RECONNECT_SUCCESS")) {
                final JsonValue data = json.get("data");
                Gdx.app.postRunnable(() -> {
                    mainGame.isReconnecting = false;
                    mainGame.setInputBlocked(false);
                    mainGame.reconnectingOverlay.hide();
                    reconnectAttempted = false;
                    mainGame.isDisconnected = false;
                    System.out.println("[КЛИЕНТ] Переподключение успешно!");
                    restoreGameState(data);
                });
                return;
            }
            if (type.equals("RECONNECT_FAILED")) {
                String err = json.get("data").getString("message", "");
                Gdx.app.postRunnable(() -> {
                    mainGame.isReconnecting = false;
                    mainGame.setInputBlocked(false);
                    mainGame.reconnectingOverlay.hide();
                    mainGame.isDisconnected = false; // сброс, чтобы не вис оверлей

                    // Очищаем всё, что связано с комнатой
                    mainGame.currentRoomId = null;
                    mainGame.players.clear();
                    mainGame.myResourceId = -1;
                    mainGame.currentActivePlayerId = -1;
                    mainGame.boardScreen = null;
                    // Если есть сохранённые учётные данные, оставляем их, но переходим в меню
                    mainGame.setScreen(new MenuScreen(mainGame, "Не удалось переподключиться: " + err));
                });
                return;
            }

        } catch (Exception e) {
            System.err.println("[КЛИЕНТ] Ошибка парсинга: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[КЛИЕНТ] Соединение закрыто. " + code + " Причина: " + reason);
        if (mainGame.gameClient != this) {
            System.out.println("[КЛИЕНТ] Этот клиент устарел, игнорируем onClose");
            return;
        }
        // Сбрасываем флаг, чтобы не блокировать повторные попытки
        reconnectAttempted = false;

        if ((remote || code != 1000) && !mainGame.isReconnecting) {
            // Показываем оверлей в любом случае (и при наличии комнаты, и без)
            mainGame.isReconnecting = true;
            mainGame.reconnectTimer = 0;
            mainGame.setInputBlocked(true);
            Gdx.app.postRunnable(() -> mainGame.reconnectingOverlay.show());

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    attemptReconnect();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    // GameClient.java

    private void attemptReconnect() {
        synchronized (this) {
            if (reconnectAttempted) {
                System.out.println("[КЛИЕНТ] Уже выполняется попытка реконнекта, пропускаем");
                return;
            }
            reconnectAttempted = true;
        }

        final URI oldUri = this.getURI();
        final long startTime = System.currentTimeMillis();
        final long MAX_RECONNECT_TIME = 60000;

        new Thread(() -> {
            try {
                while (System.currentTimeMillis() - startTime < MAX_RECONNECT_TIME) {
                    // Закрываем старого клиента, если он открыт
                    GameClient oldClient = mainGame.gameClient;
                    if (oldClient != null && oldClient.isOpen()) {
                        oldClient.close();
                    }

                    GameClient newClient = null;
                    try {
                        newClient = new GameClient(oldUri, mainGame);
                        newClient.setConnectionLostTimeout(3);
                        newClient.connect();

                        // Ждём до 2 секунд, проверяя каждые 100 мс
                        int waited = 0;
                        while (waited < 2000 && !newClient.isOpen()) {
                            Thread.sleep(100);
                            waited += 100;
                        }

                        if (newClient.isOpen()) {
                            String json = null;
                            // Случай 1: есть комната и ID → RECONNECT
                            if (mainGame.currentRoomId != null && mainGame.myResourceId != -1) {
                                json = String.format(
                                    "{\"type\":\"RECONNECT\", \"data\":{\"roomId\":\"%s\", \"userId\":%d, \"playerId\":%d}}",
                                    mainGame.currentRoomId, mainGame.myResourceId, mainGame.myResourceId
                                );
                            }
                            // Случай 2: есть сохранённые логин/пароль → LOGIN
                            else if (mainGame.hasSavedCredentials()) {
                                json = String.format(
                                    "{\"type\":\"LOGIN\", \"data\":{\"username\":\"%s\", \"password\":\"%s\"}}",
                                    mainGame.getSavedUsername(), mainGame.getSavedPassword()
                                );
                            }
                            // Случай 3: нет данных — просто восстанавливаем соединение
                            else {
                                // Успешно подключились, но не нужно отправлять никакие данные.
                                // Заменяем клиент, сбрасываем реконнект и выходим.
                                mainGame.gameClient = newClient;
                                Gdx.app.postRunnable(() -> {
                                    mainGame.isReconnecting = false;
                                    mainGame.setInputBlocked(false);
                                    mainGame.reconnectingOverlay.hide();
                                    mainGame.isDisconnected = false;
                                    // isDisconnected оставляем false
                                    System.out.println("[КЛИЕНТ] Соединение восстановлено (экран логина)");
                                });
                                reconnectAttempted = false;
                                return;
                            }

                            if (json != null) {
                                newClient.send(json);
                                mainGame.gameClient = newClient;
                                reconnectAttempted = false;
                                System.out.println("[КЛИЕНТ] Успешное переподключение, старый клиент заменён, отправлен запрос: " + json);
                                return;
                            }
                        } else {
                            if (newClient != null) newClient.close();
                        }
                    } catch (Exception e) {
                        System.err.println("[КЛИЕНТ] Ошибка при попытке подключения: " + e.getMessage());
                        if (newClient != null) newClient.close();
                    }

                    Thread.sleep(2000); // пауза перед следующей попыткой
                }

                // Время вышло – окончательная потеря соединения
                Gdx.app.postRunnable(() -> {
                    mainGame.isReconnecting = false;
                    mainGame.setInputBlocked(false);
                    mainGame.reconnectingOverlay.hide();
                    mainGame.isDisconnected = true;
                    reconnectAttempted = false;
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                reconnectAttempted = false;
            }
        }).start();
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("[КЛИЕНТ] Ошибка сети!");
        ex.printStackTrace();
        if (mainGame.gameClient != this) {
            System.out.println("[КЛИЕНТ] Этот клиент устарел, игнорируем onError");
            return;
        }
        // Включаем глобальный оверлей
        mainGame.isDisconnected = true;
    }

    /** * Вспомогательный метод-фабрика.
     * Сюда надо просто добавить новый 'case' при создании новой мини-игры
     */
    private com.badlogic.gdx.Screen createMiniGameScreen(String type, com.ox.tictactoe.screens.BoardScreen board, int row, int col) {
        switch (type) {
            case "BUTTON":
                return new com.ox.tictactoe.screens.minigames.ButtonGame(mainGame, board, col, row);
            case "CHOSEN":
                return new com.ox.tictactoe.screens.minigames.ChosenGame(mainGame, board, col, row);
            case "COLOR_GAME":
                return new com.ox.tictactoe.screens.minigames.ColorGame(mainGame, board, col, row); // Подключили!
            // Сюда добавишь: case "NEW_GAME": return new NewGameScreen(...);
            case "CARD_MEMORY":
                return new com.ox.tictactoe.screens.minigames.CardMemoryGame(mainGame, board, col, row);
            default:
                return null;
        }
    }

    /**
     * Универсальный метод отправки JSON (Map) на сервер
     */
    public void sendJson(Map<String, Object> messageMap) {
        if (!this.isOpen()) return;
        try {
            // Используем стандартный StringBuilder или простую склейку для гарантии чистоты
            com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
            json.setOutputType(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
            // Зануляем всё, что может добавить лишние поля
            json.setTypeName(null);
            json.setUsePrototypes(false);
            json.setIgnoreUnknownFields(true);

            String jsonString = json.toJson(messageMap);

            // Костыль: если LibGDX всё еще пихает {value: "CLICK"}, принудительно чистим
            // Но с setTypeName(null) должен уйти мусор.
            this.send(jsonString);
            System.out.println("[КЛИЕНТ] Отправлено: " + jsonString);
        } catch (Exception e) {
            System.out.println("[КЛИЕНТ] Ошибка конвертации: " + e.getMessage());
        }
    }

    public void sendCellAttack(int row, int col) {
        // ВАЖНО: ключи должны быть "row" и "col", как в CellAttackHandler
        String json = String.format(
            "{\"type\":\"CELL_ATTACK\", \"data\":{\"row\":%d, \"col\":%d}}",
            row, col
        );
        this.send(json);
    }

    //Список всех игроков
    private void updatePlayers(JsonValue json) {
        try {
            JsonValue data = json.get("data");
            if (data == null) return;

            mainGame.myResourceId = data.getInt("yourId", -1);

            JsonValue playersArray = data.get("players");
            if (playersArray != null && playersArray.isArray()) {
                mainGame.players.clear();
                for (JsonValue pJson : playersArray) {
                    int id = pJson.getInt("id", 0);
                    String name = pJson.getString("name", "Unknown");

                    // Вытаскиваем токен, который прислал сервер
                    String token = pJson.getString("token", "SUN");

                    boolean isMe = (id == mainGame.myResourceId);

                    // Передаем новый параметр в конструктор
                    mainGame.players.add(new com.ox.tictactoe.PlayerModel(id, name, isMe, token));
                }
                Gdx.app.log("CLIENT", "Список игроков обновлен. Мой ID: " + mainGame.myResourceId);
            }
        } catch (Exception e) {
            Gdx.app.error("CLIENT", "Ошибка при обновлении списка игроков: " + e.getMessage());
        }
    }

    public void sendLogin(String username, String password) {
        this.pendingUsername = username;
        this.pendingPassword = password;
        // Формируем чистую строку без помощи парсеров LibGDX
        String json = String.format(
            "{\"type\":\"LOGIN\", \"data\":{\"username\":\"%s\", \"password\":\"%s\"}}",
            username, password
        );
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлен логин: " + json);
    }

    public void sendRegister(String username, String password) {
        this.pendingUsername = username;
        this.pendingPassword = password;
        String json = String.format(
            "{\"type\":\"REGISTER\", \"data\":{\"username\":\"%s\", \"password\":\"%s\"}}",
            username, password
        );
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлена регистрация: " + json);
    }

    public void sendCreateLobby(int playerCount, int boardSize) {
        String json = String.format(
            "{\"type\":\"CREATE_LOBBY\", \"data\":{\"playerCount\":%d, \"boardSize\":%d}}",
            playerCount, boardSize
        );
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлен CREATE_LOBBY: " + playerCount + " игроков, доска " + boardSize + "x" + boardSize);
    }
    public void sendJoinRandomLobby() {
        String json = "{\"type\":\"JOIN_RANDOM_LOBBY\", \"data\":{}}";
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлен JOIN_RANDOM_LOBBY");
    }
    public void sendLeaveRoom() {
        String json = "{\"type\":\"LEAVE_ROOM\", \"data\":{}}";
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлен LEAVE_ROOM");
    }
    public void sendDeselectToken() {
        String json = "{\"type\":\"TOKEN_DESELECT\", \"data\":{}}";
        this.send(json);
        System.out.println("[КЛИЕНТ] Отправлен TOKEN_DESELECT");
    }
    private void restoreGameState(JsonValue data) {
        System.out.println("[RECONNECT] Данные: " + data.toString());
        // Обновляем MainGame
        mainGame.currentBoardSize = data.getInt("boardSize", 3);
        mainGame.currentActivePlayerId = data.getInt("activePlayerId", -1);
        mainGame.turnTimeoutSeconds = data.getInt("turnTimeoutSeconds", 180);
        mainGame.myResourceId = data.getInt("yourId", -1);
        mainGame.currentRoomId = data.getString("roomId", null);

        // Восстанавливаем список игроков
        mainGame.players.clear();
        JsonValue playersArray = data.get("players");
        for (JsonValue p : playersArray) {
            int id = p.getInt("id");
            String name = p.getString("name");
            String token = p.getString("token");
            mainGame.players.add(new PlayerModel(id, name, id == mainGame.myResourceId, token));
        }
        String gamePhase = data.getString("gamePhase", "");
        if ("WAITING_FOR_TOKENS".equals(gamePhase)) {
            TokenSelectionScreen tokenScreen = new TokenSelectionScreen(mainGame);
            tokenScreen.restoreState(data);
            mainGame.setScreen(tokenScreen);
            return;
        }
        if ("WAITING_FOR_PLAYERS".equals(gamePhase)) {

            // Показываем экран загрузки с кнопкой выхода (игрок ждёт других)
            LoadingScreen ls = new LoadingScreen(mainGame, true);
            mainGame.setScreen(ls);
            return;
        }
        // Создаём BoardScreen и передаём состояние доски
        BoardScreen board = new BoardScreen(mainGame);
        board.setActivePlayer(mainGame.currentActivePlayerId);
        float timeLeft = data.getFloat("timeLeftForCurrentTurn", mainGame.turnTimeoutSeconds);
        board.setRemainingTurnTime(timeLeft);
        // Восстанавливаем доску (передаём двумерный массив)
        JsonValue boardState = data.get("boardState");
        if (boardState != null) {
            board.restoreBoardState(boardState);
        }

        JsonValue minigame = data.get("minigame");
        if (minigame != null) {
            JsonValue mgState = minigame.get("state");
            boolean miniGameFinished = mgState.getBoolean("finished", false);
            if (!miniGameFinished) {
                String gameType = minigame.getString("gameType");
                int row = minigame.getInt("pendingRow");
                int col = minigame.getInt("pendingCol");

                com.badlogic.gdx.Screen gameScreen = createMiniGameScreen(gameType, board, row, col);
                if (gameScreen instanceof MiniGameInterface) {
                    ((MiniGameInterface) gameScreen).setup(mainGame.myResourceId, mainGame.players);
                    ((MiniGameInterface) gameScreen).restoreState(mgState);
                }
                mainGame.setScreen(gameScreen);
                return; // не показываем BoardScreen
            }
            // иначе (мини-игра завершена) просто показываем BoardScreen
        }

// Если нет мини-игры или она завершена, показываем доску
        mainGame.setScreen(board);
    }

}
