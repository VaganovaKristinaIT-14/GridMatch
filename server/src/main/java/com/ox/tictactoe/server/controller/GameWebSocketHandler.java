package com.ox.tictactoe.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ox.tictactoe.server.model.GameLog;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.model.User;
import com.ox.tictactoe.server.repository.GameLogRepository;
import com.ox.tictactoe.server.repository.UserRepository;
import com.ox.tictactoe.server.service.GameMessageRouter;
import com.ox.tictactoe.server.service.MatchmakingService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

// Для шифрования паролей
import com.ox.tictactoe.server.service.PasswordService;


@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final MatchmakingService matchmakingService;
    private final GameMessageRouter messageRouter;
    private final GameLogRepository gameLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordService passwordService;


    @Value("${app.auth.disabled:false}")
    private boolean authDisabled;
    @PostConstruct
    public void init() {
        System.out.println("🔧 AUTH DISABLED MODE: " + authDisabled);
    }
    public GameWebSocketHandler(MatchmakingService matchmakingService,
                                GameMessageRouter messageRouter,
                                GameLogRepository gameLogRepository,
                                UserRepository userRepository,
                                PasswordService passwordService) {
        this.matchmakingService = matchmakingService;
        this.messageRouter = messageRouter;
        this.gameLogRepository = gameLogRepository;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    private boolean authenticate(WebSocketSession session, Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");

        if (username == null || password == null) {
            return false;
        }

        User user = userRepository.findByUsername(username);

        if (user != null && passwordService.checkPassword(password, user.getPassword())) {
            session.getAttributes().put("authenticated", true);
            session.getAttributes().put("userId", user.getId());
            session.getAttributes().put("username", user.getUsername());
            System.out.println("✅ Авторизован: " + username + " (ID: " + user.getId() + ")");
            return true;
        }

        System.out.println("❌ Ошибка авторизации: " + username);
        return false;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Player connected: " + session.getId());
        if (authDisabled) {
            session.getAttributes().put("authenticated", true);
            session.getAttributes().put("userId", 999L);
            session.getAttributes().put("username", "auto_auth_user");
            System.out.println("🔓 [AUTH DISABLED] Автоматическая авторизация при подключении");


        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("=== PLAYER DISCONNECTED: " + session.getId() + " ===");
        matchmakingService.disconnectPlayer(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            Map<String, Object> jsonMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            String type = jsonMap.get("type") != null ? String.valueOf(jsonMap.get("type")) : "";
            Object dataObj = jsonMap.get("data");
            Map<String, Object> data = (Map<String, Object>)(dataObj instanceof Map ? (Map)dataObj : new HashMap());
            if ("RECONNECT".equals(type)) {
                // Сообщение о переподключении - обрабатываем без авторизации
                messageRouter.route(type, session, data);
                return;
            }
            // ========== АВТОРИЗАЦИЯ ==========
            if (session.getAttributes().get("authenticated") == null) {

                // === РЕЖИМ: АВТОРИЗАЦИЯ ОТКЛЮЧЕНА ===
                if (authDisabled) {
                    // Автоматически авторизуем любого клиента
                    session.getAttributes().put("authenticated", true);
                    session.getAttributes().put("userId", 999L);
                    session.getAttributes().put("username", "auto_auth_user");
                    System.out.println("🔓 [AUTH DISABLED] Автоматическая авторизация");
                    // Продолжаем обработку сообщения (не возвращаем)
                }
                // === РЕЖИМ: НОРМАЛЬНАЯ АВТОРИЗАЦИЯ ===
                else {
                    // LOGIN
                    if ("LOGIN".equals(type)) {
                        String username = (String) data.get("username");
                        String password = (String) data.get("password");

                        if (username == null || password == null) {
                            System.out.println("❌ Ошибка логина: username или password null");
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Имя пользователя и пароль обязательны"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        User user = userRepository.findByUsername(username);

                        if (user == null) {
                            System.out.println("❌ Ошибка логина: пользователь не найден - " + username);
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Пользователь не найден"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        if (!passwordService.checkPassword(password, user.getPassword())) {
                            System.out.println("❌ Ошибка логина: неверный пароль для " + username);
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Неверный пароль"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        // Успешный логин
                        session.getAttributes().put("authenticated", true);
                        session.getAttributes().put("userId", user.getId());
                        session.getAttributes().put("username", user.getUsername());
                        System.out.println("✅ Авторизован: " + username + " (ID: " + user.getId() + ")");

                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "AUTH_SUCCESS");
                        response.put("data", Map.of(
                            "userId", user.getId(),
                            "username", user.getUsername()
                        ));
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                        return;
                    }

                    // REGISTER
                    if ("REGISTER".equals(type)) {
                        String username = (String) data.get("username");
                        String password = (String) data.get("password");

                        // ===== ВАЛИДАЦИЯ ИМЕНИ =====
                        if (username == null || username.trim().isEmpty()) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Имя пользователя не может быть пустым"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        if (username.length() > 15) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Имя пользователя не должно превышать 15 символов"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        if (!username.matches("^[A-Za-zА-Яа-я0-9_]+$")) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Имя пользователя может содержать только буквы (русские/английские), цифры и символ _"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        // ===== ВАЛИДАЦИЯ ПАРОЛЯ =====
                        if (password == null || password.trim().isEmpty()) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Пароль не может быть пустым"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        if (password.length() < 3) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Пароль должен содержать минимум 3 символа"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        User existingUser = userRepository.findByUsername(username);
                        if (existingUser != null) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("type", "AUTH_ERROR");
                            error.put("data", Map.of("message", "Имя пользователя уже занято"));
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            return;
                        }

                        // Хешируем пароль перед сохранением
                        String hashedPassword = passwordService.hashPassword(password);
                        User newUser = new User(username, hashedPassword, 0);
                        userRepository.save(newUser);

                        System.out.println("✅ Зарегистрирован новый пользователь: " + username);

                        session.getAttributes().put("authenticated", true);
                        session.getAttributes().put("userId", newUser.getId());
                        session.getAttributes().put("username", newUser.getUsername());

                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "AUTH_SUCCESS");
                        response.put("data", Map.of(
                            "userId", newUser.getId(),
                            "username", newUser.getUsername()
                        ));
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                        return;
                    }
                    if ("GET_ME".equals(type)) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "ME");
                        response.put("data", Map.of("authenticated", false));
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                        return;  // не закрываем соединение!
                    }
                    // Неизвестный тип сообщения
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }
            }
            // ========== КОНЕЦ АВТОРИЗАЦИИ ==========

            // ========== ЛОГИКА ДЛЯ АВТОРИЗОВАННЫХ ==========
            // ===== LOGOUT =====
            if ("LOGOUT".equals(type)) {
                session.getAttributes().clear();

                Map<String, Object> response = new HashMap<>();
                response.put("type", "LOGOUT_SUCCESS");
                response.put("data", Map.of("message", "Вы вышли из системы"));
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                return;
            }

            // ===== GET_ME =====
            if ("GET_ME".equals(type)) {
                Long userId = (Long) session.getAttributes().get("userId");
                String username = (String) session.getAttributes().get("username");

                if (userId != null && username != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "ME");
                    response.put("data", Map.of(
                        "userId", userId,
                        "username", username,
                        "authenticated", true
                    ));
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                } else {
                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "ME");
                    response.put("data", Map.of(
                        "authenticated", false
                    ));
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
                return;
            }

            String roomId = "NO_ROOM";
            GameRoom room = matchmakingService.getRoomByPlayer(session);
            if (room != null) {
                roomId = room.getRoomId();
            }

            // 2. Получаем IP-адрес (вычисляем один раз и сохраняем в сессию)
            String ipAddress = (String) session.getAttributes().get("ipAddress");
            if (ipAddress == null) {
                java.net.InetSocketAddress remoteAddress = session.getRemoteAddress();
                if (remoteAddress != null) {
                    ipAddress = remoteAddress.getAddress().getHostAddress();
                    session.getAttributes().put("ipAddress", ipAddress);
                }
            }

            // 3. Получаем ID пользователя из сессии
            Long userId = (Long) session.getAttributes().get("userId");

            // 4. Сохраняем расширенный лог в базу
            GameLog log = new GameLog(roomId, type, payload);
            log.setUserId(userId);
            log.setIpAddress(ipAddress);
            gameLogRepository.save(log);

            messageRouter.route(type, session, data);

        } catch (Exception e) {
            System.err.println("Ошибка обработки сообщения: " + payload);
            e.printStackTrace();
        }
    }

}
