package com.ox.tictactoe.server.service.handler;

import com.ox.tictactoe.server.config.NetworkConfig;
import com.ox.tictactoe.server.model.GameRoom;
import com.ox.tictactoe.server.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class JoinRandomLobbyHandler implements GameActionHandler {

    private final MatchmakingService matchmakingService;

    public JoinRandomLobbyHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public String getSupportedType() {
        return NetworkConfig.MessageType.JOIN_RANDOM_LOBBY;
    }

    @Override
    public void handle(WebSocketSession session, GameRoom room, Map<String, Object> data) {
        // room будет null, так как игрок ещё не в комнате
        matchmakingService.joinRandomLobby(session);
    }
}
