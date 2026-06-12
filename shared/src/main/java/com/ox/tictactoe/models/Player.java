package com.ox.tictactoe.models;


public class Player {
    private String name;
    private int avatarId;

    public Player(String name, int avatarId){
        this.name = name;
        this.avatarId = avatarId;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        if (!value.isBlank()) {
            name = value;
        }
    }

    public int getAvatar() {
        return avatarId;
    }

    public void setAvatar(int value) {
        if (avatarId >= 0) {
            avatarId = value;
        }
    }
}
