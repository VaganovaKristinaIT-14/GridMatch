package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class CreateLobbyHandler implements GameActionHandler {

    private final MatchmakingService matchmakingService;

    public CreateLobbyHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.CREATE_LOBBY;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        // room будет null, так как игрок ещё не в комнате

        // Получаем параметры из data
        int playerCount = (int) data.getOrDefault(NetworkConfig.Keys.PLAYER_COUNT, 2);
        int boardSize = (int) data.getOrDefault(NetworkConfig.Keys.BOARD_SIZE, 3);

        // Валидация
        if (playerCount < 2 || playerCount > 4) {
            playerCount = 2;
        }
        if (boardSize < 2 || boardSize > 4) {
            boardSize = 3;
        }

        // Создаём лобби
        matchmakingService.createLobby(session, playerCount, boardSize);
    }
}
