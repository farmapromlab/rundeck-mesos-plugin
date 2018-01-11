package com.farmaprom.rundeck.plugin.state;

import org.apache.mesos.v1.Protos;

public final class State<FwId> {

    public static final Protos.TaskState[] finalTaskStatuses = {
            Protos.TaskState.TASK_FAILED,
            Protos.TaskState.TASK_LOST,
            Protos.TaskState.TASK_KILLED,
            Protos.TaskState.TASK_FINISHED,
            Protos.TaskState.TASK_ERROR
    };

    private final String name;
    private final String hostName;
    private final FwId fwId;
    private final double cpusPerTask;
    private final double memMbPerTask;
    private final String resourceRole;

    public State(
            final FwId fwId,
            final String resourceRole,
            final double cpusPerTask,
            final double memMbPerTask,
            final String name,
            final String hostName
    ) {
        this.fwId = fwId;
        this.resourceRole = resourceRole;
        this.cpusPerTask = cpusPerTask;
        this.memMbPerTask = memMbPerTask;
        this.name = name;
        this.hostName = hostName;
    }

    public FwId getFwId() {
        return fwId;
    }

    public String getResourceRole() {
        return resourceRole;
    }

    public double getCpusPerTask() {
        return cpusPerTask;
    }

    public double getMemMbPerTask() {
        return memMbPerTask;
    }

    public String getName() {
        return name;
    }

    public String getHostName() {
        return hostName;
    }
}