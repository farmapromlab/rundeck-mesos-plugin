package com.farmaprom.rundeck.plugin.logger;

import org.apache.mesos.v1.Protos;

import java.time.LocalDateTime;
import java.util.HashMap;

public class LoggerWrapper {

    private HashMap<Integer, Log> messages = new HashMap<>();

    public Protos.FrameworkID frameworkID;
    public Protos.MasterInfo masterInfo;
    public Protos.TaskInfo task;
    public Protos.TaskStatus taskStatus;
    public boolean mesosTailWait = true;

    public HashMap<Integer, Log> getMessages() {
        return messages;
    }

    private LoggerWrapper addMessage(Integer level, String message) {
        messages.put(messages.size() + 1, new Log(level, message));

        return this;
    }

    public LoggerWrapper debug(String message) {
        this.addMessage(5, "Time = " +  LocalDateTime.now() + ", " + message);

        return this;
    }

    public LoggerWrapper error(String message) {
        this.addMessage(0, "Time = " +  LocalDateTime.now() + ", " + message);

        return this;
    }

    public void stoptMesosTailWait() {
        this.mesosTailWait = false;
    }
}
