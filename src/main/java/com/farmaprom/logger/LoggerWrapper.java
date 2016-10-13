package com.farmaprom.logger;

import java.util.HashMap;

import org.apache.mesos.Protos;

public class LoggerWrapper {

    private HashMap<Integer, String> messages = new HashMap<Integer, String>();

    public Protos.FrameworkID frameworkID;
    public Protos.MasterInfo masterInfo;
    public Protos.TaskInfo task;
    public Protos.TaskStatus taskStatus;

    public HashMap<Integer, String> getMessages() {
        return messages;
    }

    private LoggerWrapper addMessage(String message) {
        messages.put(messages.size() + 1, message);

        return this;
    }

    public LoggerWrapper info(String message) {
        this.addMessage(message);
        return this;
    }
}
