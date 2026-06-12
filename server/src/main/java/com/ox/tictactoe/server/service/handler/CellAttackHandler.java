package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.game.ButtonGameEngine;
import com.ox.tictactoe.server.game.CardMemoryGameEngine;
import com.ox.tictactoe.server.game.ChosenGameEngine;
import com.ox.tictactoe.server.game.ColorGameEngine;
import com.ox.tictactoe.server.game.MiniGameEngine;
import com.ox.tictactoe.server.model.GameRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class CellAttackHandler implements GameActionHandler {

    private final Random random = new Random();

    private final List<GameCreator> availableGames = Arrays.asList(
        new GameCreator() {
            @Override
            public String getGameType() { return "BUTTON"; }
            @Override
            public MiniGameEngine createEngine() { return new ButtonGameEngine(); }
        },
        new GameCreator() {
            @Override
            public String getGameType() { return "CHOSEN"; }
            @Override
            public MiniGameEngine createEngine() { return new ChosenGameEngine(); }
        },
        new GameCreator() {
            @Override
            public String getGameType() { return "COLOR_GAME"; }
            @Override
            public MiniGameEngine createEngine() { return new ColorGameEngine(); }
        },
        new GameCreator() {
            @Override
            public String getGameType() { return "CARD_MEMORY"; }
            @Override
            public MiniGameEngine createEngine() { return new CardMemoryGameEngine(); }
        }
    );

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.CELL_ATTACK;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        int senderId = room.getPlayerId(session);
        room.updateLastActionTime(senderId);
        int currentTurnId = room.getCurrentTurnPlayerId();

        if (senderId != currentTurnId) {
            System.out.println("[ATTACK REJECTED] Игрок " + senderId + " пытался сходить не в свой ход. Сейчас ходит: " + currentTurnId);
            return;
        }

        int row = (int) data.get(NetworkConfig.Keys.ROW);
        int col = (int) data.get(NetworkConfig.Keys.COL);

        System.out.println("Игрок " + senderId + " атакует клетку [" + row + ", " + col + "]");

        room.setPendingRow(row);
        room.setPendingCol(col);
        room.setPendingAttackerId(senderId);   // СОХРАНЯЕМ АТАКУЮЩЕГО


        room.setGamePhase("MINIGAME_ACTIVE");
        GameCreator selectedGame = availableGames.get(random.nextInt(availableGames.size()));
        MiniGameEngine gameEngine = selectedGame.createEngine();
        room.setCurrentGame(gameEngine);
        gameEngine.start(room);
        room.setCurrentGame(gameEngine);
        gameEngine.start(room);
    }

    private interface GameCreator {
        String getGameType();
        MiniGameEngine createEngine();
    }
}
