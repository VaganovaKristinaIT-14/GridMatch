package com.ox.tictactoe.server.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_logs")
public class GameLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String roomId;
    private String messageType;
    @Column(columnDefinition = "TEXT")
    private String jsonPayload;
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    private LocalDateTime timestamp = LocalDateTime.now();

    public GameLog() {}

    public GameLog(String roomId, String messageType, String jsonPayload) {
        this.roomId = roomId;
        this.messageType = messageType;
        this.jsonPayload = jsonPayload;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getJsonPayload() { return jsonPayload; }
    public void setJsonPayload(String jsonPayload) { this.jsonPayload = jsonPayload; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
