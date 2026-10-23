package com.chunklock.model;

public enum ProgressTask {
    PLAYTIME("playtime", "play minutes"),
    BLOCKS("blocks", "blocks mined"),
    HOSTILE_MOBS("hostile-mobs", "hostile mobs defeated");

    private final String configName;
    private final String description;

    ProgressTask(String configName, String description) {
        this.configName = configName;
        this.description = description;
    }

    public String configName() {
        return configName;
    }

    public String description() {
        return description;
    }
}
