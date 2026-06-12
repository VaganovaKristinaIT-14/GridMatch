package com.ox.tictactoe;
import java.util.HashMap;
import java.util.Map;

/**
 * Карточка игрока.
 */
public class PlayerModel {
    public int id;
    public String nickname;
    public boolean isLocal;
    public String token; // ДОБАВЛЕНО: Для хранения выбранной фишки (SUN, TREE и т.д.)

    public Map<String, Object> properties = new HashMap<>();

    public PlayerModel(int id, String nickname, boolean isLocal, String token) {
        this.id = id;
        this.nickname = nickname;
        this.isLocal = isLocal;
        this.token = token; // Инициализируем фишку
    }
}
