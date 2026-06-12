package com.ox.tictactoe.server.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация сетевых сообщений для всех мини-игр.
 * Содержит все типы сообщений и их структуру.
 */
public class NetworkConfig {

    /**
     * Типы сообщений, общие для всех игр
     */
    public static final class MessageType {

        /** ----- Системные ----- */
        public static final String PING = "PING";           // проверка соединения
        public static final String PONG = "PONG";           // ответ на ping
        public static final String ERROR = "ERROR";         // общая ошибка

        /** ----- Авторизация ----- */
        public static final String LOGIN = "LOGIN";
        public static final String REGISTER = "REGISTER";
        public static final String AUTH_SUCCESS = "AUTH_SUCCESS";
        public static final String AUTH_ERROR = "AUTH_ERROR";
        public static final String LOGOUT = "LOGOUT";
        public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
        public static final String GET_ME = "GET_ME";
        public static final String ME = "ME";

        /** ----- Матчмейкинг ----- */
        public static final String WAITING = "WAITING_FOR_OPPONENT"; // Ожидание соперника
        public static final String OPPONENT_FOUND = "OPPONENT_FOUND"; // Соперник найден
        public static final String OPPONENT_LEFT = "OPPONENT_LEFT"; // Соперник отключился

        /** ----- Выбор фишек ----- */
        public static final String TOKEN_SELECTION_REQUEST = "TOKEN_SELECTION_REQUEST"; // Сервер просит выбрать фишку
        public static final String TOKEN_SELECTION = "TOKEN_SELECTION"; // Игрок отправляет выбор
        public static final String TOKEN_SELECTION_COMPLETE = "TOKEN_SELECTION_COMPLETE"; // Все выбрали
        public static final String TOKEN_UNAVAILABLE = "TOKEN_UNAVAILABLE"; // Фишка уже занята
        public static final String TOKEN_TIMEOUT = "TOKEN_TIMEOUT"; // Время вышло, назначена случайная
        // Внутри класса MessageType добавьте:
        public static final String TOKEN_DESELECT = "TOKEN_DESELECT";
        /** ----- Игровые сообщения -----*/
        public static final String PLAYER_MOVE = "PLAYER_MOVE"; // Ход игрока
        public static final String CELL_ATTACK = "CELL_ATTACK"; // Атака клетки(на основном поле)
        public static final String GAME_STATE = "GAME_STATE"; // Обновление состояния игры
        public static final String GAME_OVER = "GAME_OVER"; // Игра окончена

        /** ----- Действия в мини-играх ----- */
        public static final String MINIGAME_ACTION = "MINIGAME_ACTION"; // Специфические действия
        public static final String MINIGAME_RESULT = "MINIGAME_RESULT"; // Чем закончилась миниигра
        public static final String MINIGAME_START = "MINIGAME_START";  // старт мини-игры
        public static final String MINIGAME_JOIN = "MINIGAME_JOIN";    // игрок зашел в мини-игру
        public static final String MINIGAME_LEAVE = "MINIGAME_LEAVE";  // игрок вышел

        /** ----- Лобби ----- */
        public static final String CREATE_LOBBY = "CREATE_LOBBY";   // клиент → сервер (создать лобби)
        public static final String LOBBY_CREATED = "LOBBY_CREATED"; // сервер → клиент (лобби создано, roomId)
        public static final String JOIN_LOBBY = "JOIN_LOBBY";       // клиент → сервер (присоединиться по roomId)
        public static final String LOBBY_JOINED = "LOBBY_JOINED";   // сервер → клиент (успешно присоединился)
        public static final String LOBBY_UPDATE = "LOBBY_UPDATE";   // сервер → всем (обновление списка игроков)
        public static final String START_GAME = "START_GAME";       // сервер → всем (игроков достаточно, начинаем выбор фишек)
        public static final String JOIN_RANDOM_LOBBY = "JOIN_RANDOM_LOBBY";
        public static final String LEAVE_ROOM = "LEAVE_ROOM";     // клиент → сервер (выйти из комнаты)
        public static final String ALREADY_IN_GAME = "ALREADY_IN_GAME";
    }

    public static final class Keys {
        public static final String GAME_TYPE = "gameType";  // Тип миниигры(Колесо фортуны, кнопка и т.д.)
        public static final String ACTION = "action";        // "MOVE", "JUMP", "SHOOT"
        public static final String PLAYER_ID = "playerId";  // ID игрока
        public static final String MINIGAME_WINNER_ID = "minigameWinnerId";  // ID игрока, выигравшего в мини-игре
        public static final String MINIGAME_ID = "minigameId"; // ID миниигры
        public static final String GAME_WINNER_ID = "gameWinnerId"; // ID победителя всей игры
        public static final String ROOM_ID = "roomId";      // ID комнаты, в которой проходит игра
        public static final String ROW = "row";              // строка(для ходов на поле)
        public static final String COL = "col";              // столбец(для ходов на поле)
        public static final String BOARD = "board";          // состояние поля
        public static final String PLAYERS = "players";      // список игроков
        public static final String MESSAGE = "message";      // текстовые сообщения
        public static final String REASON = "reason";        // причина (отключения и т.д.)
        public static final String DURATION = "duration";    // Длительность мини-игры (секунды)
        public static final String TIME_LEFT = "timeLeft";   // Сколько осталось
        public static final String TURN_TIME = "turnTime";   // Таймер на ход
        public static final String START_TIME = "startTime";  // Время старта мини-игры
        public static final String FOUL_PLAYERS = "foulPlayers"; // Игроки, совершившие фол

        // ----- Выбор фишек -----
        public static final String SELECTED_TOKEN = "selectedToken";      // выбранная фишка
        public static final String AVAILABLE_TOKENS = "availableTokens";  // список доступных фишек
        public static final String TOKEN_SELECTIONS = "tokenSelections";  // карта {id игрока → фишка}
        public static final String YOUR_TOKEN = "yourToken";              // твоя фишка
        public static final String GAME_PHASE = "gamePhase";              // текущая фаза игры

        public static final String PLAYER_COUNT = "playerCount";
        public static final String BOARD_SIZE = "boardSize";
        public static final String PLAYERS_IN_LOBBY = "playersInLobby";
    }

    /**
     * Вспомогательный класс для создания сообщений
     */
    public static class MessageBuilder {

        /**
         * Создает сообщение с данными
         * @param type тип сообщения
         * @param data данные (может быть Map, List, String и т.д.)
         * @return сообщение в формате {type, data, timestamp}
         */
        public static Map<String, Object> createMessage(String type, Object data) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());
            return message;
        }

        /**
         * Создает простое сообщение (только текст)
         */
        public static Map<String, Object> createSimpleMessage(String type, String text) {
            Map<String, String> data = new HashMap<>();
            data.put("message", text);
            return createMessage(type, data);
        }
    }
}
