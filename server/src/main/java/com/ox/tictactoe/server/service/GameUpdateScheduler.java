package com.ox.tictactoe.server.service;

import com.ox.tictactoe.server.game.MiniGameEngine;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class GameUpdateScheduler {

    private final MatchmakingService matchmakingService;

    public GameUpdateScheduler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    /**
     * Главный игровой цикл сервера.
     * Вызывается каждые 200 мс для обновления всех активных мини-игр.
     */
    @Scheduled(fixedRate = 200)
    public void updateAllGames() {
        Collection<GameRoom> allRooms = matchmakingService.getAllRooms();

        for (GameRoom room : allRooms) {
            MiniGameEngine game = room.getCurrentGame();
            if (game != null && !game.isFinished()) {
                try {
                    game.update(0.2f, room);
                } catch (Exception e) {
                    System.err.println("[GameUpdateScheduler] Ошибка в update() для комнаты " + room.getRoomId());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Проверка таймаутов бездействия каждые 30 секунд
     */
    @Scheduled(fixedRate = 3000)
    public void checkAllTimeouts() {
        Collection<GameRoom> allRooms = matchmakingService.getAllRooms();
        for (GameRoom room : allRooms) {
            matchmakingService.checkInactivityTimeouts(room);
        }
    }
}
