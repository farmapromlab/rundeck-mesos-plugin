package com.farmaprom.logger;

import java.util.HashMap;
import com.farmaprom.utils.Log;
import org.apache.mesos.Protos;

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
        this.addMessage(5, message);

        return this;
    }

    public LoggerWrapper error(String message) {
        this.addMessage(0, message);

        return this;
    }

    public void stoptMesosTailWait() {
        this.mesosTailWait = false;
    }
}
