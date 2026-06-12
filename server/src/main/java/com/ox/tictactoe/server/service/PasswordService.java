package com.ox.tictactoe.server.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    // Создаем один экземпляр кодировщика BCrypt. Он потокобезопасен.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Хеширует (шифрует) пароль пользователя.
     * @param rawPassword Пароль в открытом виде, введенный пользователем.
     * @return Хеш пароля, который будет храниться в базе данных.
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null) {
            return null; // Или бросить исключение, если пароль не может быть null
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Проверяет, соответствует ли введенный пользователем пароль хешу, хранящемуся в базе данных.
     * @param rawPassword Пароль в открытом виде, введенный пользователем для проверки.
     * @param encodedPassword Хеш пароля, полученный из базы данных.
     * @return true, если пароль совпадает с хешем, false в противном случае.
     */
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        // Проверка на null важна, чтобы избежать NullPointerException
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        // BCryptPasswordEncoder.matches() делает всю работу:
        // 1. Извлекает соль из encodedPassword.
        // 2. Хеширует rawPassword с этой солью.
        // 3. Сравнивает полученный хеш с encodedPassword.
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
