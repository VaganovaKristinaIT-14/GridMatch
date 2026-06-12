package com.ox.tictactoe.server.model;
public class GameMessage {
    private String type;
    private String content;
    private long timestamp;

    public GameMessage() {
    }
    public GameMessage(String type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
