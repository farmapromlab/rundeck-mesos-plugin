package com.farmaprom.rundeck.plugin.logger;

import com.dtolabs.rundeck.core.execution.ExecutionLogger;
import com.farmaprom.rundeck.plugin.state.State;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.mesos.v1.Protos.URL;
import org.apache.mesos.v1.Protos.FrameworkID;
import org.apache.mesos.v1.Protos.AgentID;
import org.apache.mesos.v1.Protos.ExecutorID;
import org.apache.mesos.v1.Protos.ContainerID;
import org.apache.mesos.v1.Protos.TaskState;
import org.apache.mesos.v1.Protos.TaskStatus.Reason;

import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.apache.http.client.utils.URIBuilder;
import org.json.simple.parser.ParseException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;

import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;
import static org.apache.mesos.v1.master.Protos.Response.Type.GET_FLAGS;

public class MessosHttpTail {

    private static final String WORK_DIR = "work_dir";

    private final ExecutionLogger logger;
    private FrameworkID frameworkId;
    private AgentID agentId;
    private ExecutorID executorId;
    private ContainerID containerId;
    private TaskState taskState;
    private String taskStateMessage;
    private Reason taskStateReason;
    private URL url;
    private String agentLogDir;

    private boolean tailWait = true;
    private boolean tail = true;
    private Integer offset = 0;

    public MessosHttpTail(ExecutionLogger logger)
    {
        this.logger = logger;
    }

    private FrameworkID getFrameworkId() {
        return frameworkId;
    }

    public void setFrameworkId(FrameworkID frameworkId) {
        this.frameworkId = frameworkId;
    }

    private AgentID getAgentId() {
        return agentId;
    }

    public void setAgentId(AgentID agentId) {
        this.agentId = agentId;
    }

    private ExecutorID getExecutorId() {
        return executorId;
    }

    public void setExecutorId(ExecutorID executorId) {
        this.executorId = executorId;
    }

    private ContainerID getContainerId() {
        return containerId;
    }

    public void setContainerId(ContainerID containerId) {
        this.containerId = containerId;
    }

    public TaskState getTaskState() {
        return taskState;
    }

    public void setTaskState(TaskState taskState) {
        this.taskState = taskState;
    }

    public String getTaskStateMessage() {
        return taskStateMessage;
    }

    public void setTaskStateMessage(String taskStateMessage) {
        this.taskStateMessage = taskStateMessage;
    }

    public Reason getTaskStateReason() {
        return taskStateReason;
    }

    public void setTaskStateReason(Reason taskStateReason) {
        this.taskStateReason = taskStateReason;
    }

    private URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public void finishTail() {
        this.tailWait = false;
        this.tail = false;
    }

    public void startTail() {
        this.tailWait = false;
    }

    public void initTailStdOut() {
        try {
            while (this.tailWait || this.getUrl() == null) {
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            System.out.println("");
        }

        if (this.getUrl() != null) {
            try {
                this.initAgentWorkDir();

                System.out.println("Output stdout: ");
                while (this.tail) {
                    this.filesRead();

                    if (Arrays.asList(State.finalTaskStatuses).contains(this.getTaskState())) {
                        this.tail = false;
                        break;
                    }

                    this.filesRead();
                }

            } catch (MalformedURLException | URISyntaxException | UnirestException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public void initTailStdErr()
    {
        if (this.getUrl() != null) {
            try {
                this.initAgentWorkDir();

                System.out.println("\r\n" + "Output stderr: ");

                this.filesDownload();
            } catch (MalformedURLException | URISyntaxException | UnirestException | ParseException e) {
                e.printStackTrace();
            }
        }

    }

    private void initAgentWorkDir() throws URISyntaxException, MalformedURLException, UnirestException, ParseException {
        if (this.agentLogDir == null) {
            this.agentLogDir = "";
            HttpResponse<JsonNode> jsonResponse;

            jsonResponse = Unirest.post(this.buildAgentUrl().toString() + "/api/v1")
                    .header("Content-Type", "application/json")
                    .header("Accept-Encoding", "application/json")
                    .body("{\"type\": \"" + GET_FLAGS + "\"}")
                    .asJson();

            JSONParser parser = new JSONParser();

            JSONObject flagsObject = (JSONObject) parser.parse(jsonResponse.getBody().getObject().get("get_flags").toString());

            JSONArray flags = (JSONArray) flagsObject.get("flags");
            for (Object flag : flags) {
                JSONObject item = (JSONObject) parser.parse(flag.toString());
                if (Objects.equals(item.get("name"), WORK_DIR)) {
                    this.agentLogDir = this.buildLogDir(item.get("value").toString());
                    break;
                }
            }

            logger.log(DEBUG_LEVEL, "Agent work dir: " + this.agentLogDir);
        }
    }

    private java.net.URL buildAgentUrl() throws URISyntaxException, MalformedURLException {
        URIBuilder builder = new URIBuilder();
        builder.setScheme(this.getUrl().getScheme());
        builder.setHost(this.getUrl().getAddress().getHostname());
        builder.setPort(this.getUrl().getAddress().getPort());

        logger.log(DEBUG_LEVEL, "Agent url: " + this.getUrl());

        return builder.build().toURL();
    }

    private String buildLogDir(String workDir)
    {
        return workDir
                + "/slaves/"
                + this.getAgentId().getValue()
                + "/frameworks/"
                + this.getFrameworkId().getValue()
                + "/executors/"
                + this.getExecutorId().getValue()
                + "/runs/"
                + this.getContainerId().getValue();
    }

    private void filesRead() throws MalformedURLException, URISyntaxException {
        try {
            JSONParser parser = new JSONParser();
            HttpResponse<JsonNode> jsonResponse;

            jsonResponse = Unirest.get(this.buildAgentUrl().toString() + "/files/read.json")
                    .header("Content-Type", "application/json")
                    .header("Accept-Encoding", "application/json")
                    .queryString("path", this.agentLogDir + "/stdout")
                    .queryString("offset", this.offset)
                    .asJson();

            JSONObject fileRead = (JSONObject) parser.parse(jsonResponse.getBody().toString());

            if (fileRead.get("data") == null) {
                Thread.sleep(4000);
            }

            String data = fileRead.get("data").toString();

            if (data == null || data.length() == 0) {
                Thread.sleep(2000);
            } else {
                this.offset += data.length();
                System.out.print(data);
            }
        } catch (UnirestException | ParseException | InterruptedException e) {
            System.out.println("");
        }
    }
    private void filesDownload() throws MalformedURLException, URISyntaxException {
        HttpResponse<String> response;
        int status, numAttempts = 0;

        try {
            do {
                response = Unirest.get(this.buildAgentUrl().toString() + "/files/download.json")
                        .header("Content-Type", "application/json")
                        .header("Accept-Encoding", "application/json")
                        .queryString("path", this.agentLogDir + "/stderr")
                        .asString();

                numAttempts++;

                Thread.sleep(1000);

                status = response.getStatus();

                if (status == 200) {
                    break;
                }
            } while (numAttempts < 10);

            System.out.println(response.getBody());
        } catch (UnirestException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("");
        }
    }
}
