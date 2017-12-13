package com.farmaprom.rundeck.plugin.logger;

public class Log {
    private Integer level;
    private String message;

    public Log(Integer level, String message) {
        this.level = level;
        this.message = message;
    }

    public Integer getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
