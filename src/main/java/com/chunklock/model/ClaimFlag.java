package com.chunklock.model;

import java.util.Locale;

public enum ClaimFlag {
    PVP("pvp"),
    EXPLOSIONS("explosions"),
    CONTAINER_ACCESS("chest-access"),
    REDSTONE("redstone"),
    MOB_SPAWNING("mob-spawning");

    private final String commandName;

    ClaimFlag(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static ClaimFlag fromCommand(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.equals("container-access") || normalized.equals("containers")) {
            normalized = "chest-access";
        }
        for (ClaimFlag flag : values()) {
            if (flag.commandName.equals(normalized)) {
                return flag;
            }
        }
        return null;
    }
}
