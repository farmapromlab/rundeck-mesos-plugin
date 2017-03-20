package com.farmaprom.helpers;

import com.farmaprom.logger.LoggerWrapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.mesos.Protos;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Objects;

public class MesosTaskHelper {

    private static final int PAGE_LENGTH = 1024;
    private Boolean tail = true;

    public void mesosTailStdOut(LoggerWrapper loggerWrapper) {
        try {
            while (loggerWrapper.mesosTailWait || loggerWrapper.task == null) {
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            System.out.println("");
        }

        JSONParser parser = new JSONParser();

        String frameworkID = this.getFrameworkId(loggerWrapper);
        String mesosMasterIP = this.getMesosMasterIp(loggerWrapper);
        Integer mesosMasterPort = this.getMesosMasterPort(loggerWrapper);
        String slaveID = this.getSalveId(loggerWrapper);
        String taskID = this.getTaskId(loggerWrapper);

        String slaveHostName = this.getSlaveHostName(parser, mesosMasterIP, mesosMasterPort, slaveID);

        HttpResponse<JsonNode> jsonResponse;

        String directory = this.getDirectoryTashRunning(
                parser,
                this.getSlaveHostName(parser, mesosMasterIP, mesosMasterPort, slaveID),
                frameworkID,
                slaveID,
                taskID
        );

        String file = "stdout";

        try {
            int offset = 0;

            System.out.println("Output " + file + ":");

            while (this.tail) {
                jsonResponse = Unirest.get(this.getMesosFileReadUrl(slaveHostName))
                        .header("accept", "application/json")
                        .queryString("path", directory + "/" + file)
                        .queryString("offset", offset)
                        .queryString("length", PAGE_LENGTH)
                        .asJson();

                JSONObject fileRead = (JSONObject) parser.parse(jsonResponse.getBody().toString());

                if (jsonResponse.getStatus() != 200 || fileRead.get("data") == null) {
                    Thread.sleep(2000);
                    continue;
                }

                String data = fileRead.get("data").toString();

                if (data == null || data.length() == 0) {
                    Thread.sleep(1000);
                    continue;
                }

                offset += data.length();

                System.out.print(data);

                if (loggerWrapper.taskStatus != null) {
                    Protos.TaskState state = loggerWrapper.taskStatus.getState();

                    switch (state) {
                        case TASK_FAILED:
                        case TASK_FINISHED:
                        case TASK_KILLED:
                        case TASK_LOST:
                        case TASK_ERROR:
                            this.tail = false;
                            break;
                    }
                }

            }
        } catch (UnirestException | ParseException | InterruptedException e) {
            System.out.println("");
        }
    }


    public void stopTail() {
        this.tail = false;
    }

    public void getMesosTaskOutput(LoggerWrapper loggerWrapper) {
        JSONParser parser = new JSONParser();
        try {
            Thread.sleep(1000);

            String logs = getMesosTaskLogs(
                    parser,
                    loggerWrapper,
                    this.getMesosMasterIp(loggerWrapper),
                    this.getMesosMasterPort(loggerWrapper)
            );

            System.out.println("");
            System.out.println(logs);

        } catch (InterruptedException e) {
            System.out.println("");
        }
    }

    private String getFrameworkId(LoggerWrapper loggerWrapper) {
        return loggerWrapper.frameworkID.getValue();
    }


    private String getMesosMasterIp(LoggerWrapper loggerWrapper) {
        int ip = loggerWrapper.masterInfo.getIp();

        return String.format("%d.%d.%d.%d",
                (ip & 0xff),
                (ip >> 8 & 0xff),
                (ip >> 16 & 0xff),
                (ip >> 24 & 0xff));
    }

    private Integer getMesosMasterPort(LoggerWrapper loggerWrapper) {
        return loggerWrapper.masterInfo.getPort();
    }

    private String getSalveId(LoggerWrapper loggerWrapper) {
        return loggerWrapper.task.getSlaveId().getValue();
    }

    private String getTaskId(LoggerWrapper loggerWrapper) {
        return loggerWrapper.task.getTaskId().getValue();
    }

    private String getMesosFileReadUrl(String slaveHostName) {
        return "http://" + slaveHostName + ":5051/files/read.json";
    }

    private String getMesosTaskLogs(JSONParser parser, LoggerWrapper loggerWrapper, String mesosMasterIP, Integer mesosMasterPort) {

        String frameworkID = this.getFrameworkId(loggerWrapper);
        String slaveID = this.getSalveId(loggerWrapper);
        String taskID = this.getTaskId(loggerWrapper);

        String slaveHostName = this.getSlaveHostName(parser, mesosMasterIP, mesosMasterPort, slaveID);
        String directory = this.getDirectoryTashFinish(parser, slaveHostName, frameworkID, slaveID, taskID);

        String stderr = getLogFileData(slaveHostName, directory, "stderr");

        return "\r\n" + stderr;
    }

    private String getLogFileData(String slaveHostName, String directory, String file) {
        JSONParser parser = new JSONParser();

        HttpResponse<JsonNode> jsonResponse;
        int status, numAttempts = 0;

        try {
            do {
                jsonResponse = Unirest.get(this.getMesosFileReadUrl(slaveHostName))
                        .header("accept", "application/json")
                        .queryString("path", directory + "/" + file)
                        .queryString("offset", 0)
                        .asJson();

                numAttempts++;

                Thread.sleep(1000);

                status = jsonResponse.getStatus();

                if (status == 200) {
                    break;
                }
            } while (numAttempts < 10);


            JSONObject fileRead = (JSONObject) parser.parse(jsonResponse.getBody().toString());

            return "Output " + file + ":\r\n" + fileRead.get("data").toString() + "\r\n";
        } catch (UnirestException | ParseException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("");
        }

        return "";
    }

    private String getSlaveHostName(JSONParser parser, String mesosMasterIP, Integer mesosMasterPort, String slaveID) {

        String slaveHostName = "";

        try {
            HttpResponse<String> masterSlavesResponse = Unirest.post("http://" + mesosMasterIP + ":" + mesosMasterPort + "/master/slaves")
                    .asString();


            JSONObject masterSlaveBody = (JSONObject) parser.parse(masterSlavesResponse.getBody());

            JSONArray salves = (JSONArray) masterSlaveBody.get("slaves");

            for (Object salve : salves) {
                JSONObject slave = (JSONObject) parser.parse(salve.toString());
                if (Objects.equals(slaveID, slave.get("id").toString())) {
                    slaveHostName = slave.get("hostname").toString();
                }
            }
        } catch (UnirestException | ParseException e) {
            e.printStackTrace();
        }

        return slaveHostName;
    }

    private String getDirectoryTashFinish(JSONParser parser, String slaveHostName, String executeFrameworkID, String slaveID, String taskID) {
        String directory = "";

        try {
            HttpResponse<String> statsResponse = Unirest.post("http://" + slaveHostName + ":5051/state.json")
                    .asString();

            JSONObject jsonBody = (JSONObject) parser.parse(statsResponse.getBody());

            JSONArray completedFrameworks = (JSONArray) jsonBody.get("completed_frameworks");
            for (Object completedFramework : completedFrameworks) {
                JSONObject framework = (JSONObject) parser.parse(completedFramework.toString());

                if (Objects.equals(executeFrameworkID, framework.get("id").toString())) {
                    JSONArray completedExecutors = (JSONArray) framework.get("completed_executors");
                    for (Object completedExecutor : completedExecutors) {
                        JSONObject executor = (JSONObject) parser.parse(completedExecutor.toString());

                        if (Objects.equals(taskID, executor.get("id").toString())) {
                            JSONArray completedTasks = (JSONArray) executor.get("completed_tasks");
                            for (Object completedTask : completedTasks) {
                                JSONObject task = (JSONObject) parser.parse(completedTask.toString());

                                if (Objects.equals(slaveID, task.get("slave_id").toString())) {
                                    directory = executor.get("directory").toString();

                                }
                            }
                        }
                    }
                }
            }
        } catch (UnirestException | ParseException e) {
            e.printStackTrace();
        }

        return directory;
    }


    private String getDirectoryTashRunning(JSONParser parser, String slaveHostName, String executeFrameworkID, String slaveID, String taskID) {
        String directory = "";

        try {
            int i = 5;
            while(this.tail && Objects.equals(directory, "") && i > 1) {
                HttpResponse<String> statsResponse = Unirest.post("http://" + slaveHostName + ":5051/state.json")
                        .asString();

                JSONObject jsonBody = (JSONObject) parser.parse(statsResponse.getBody());

                JSONArray completedFrameworks = (JSONArray) jsonBody.get("frameworks");
                for (Object completedFramework : completedFrameworks) {
                    JSONObject framework = (JSONObject) parser.parse(completedFramework.toString());
                    if (Objects.equals(executeFrameworkID, framework.get("id").toString())) {
                        JSONArray completedExecutors = (JSONArray) framework.get("executors");
                        for (Object completedExecutor : completedExecutors) {
                            JSONObject executor = (JSONObject) parser.parse(completedExecutor.toString());
                            if (Objects.equals(taskID, executor.get("id").toString())) {
                                JSONArray completedTasks = (JSONArray) executor.get("tasks");
                                for (Object completedTask : completedTasks) {
                                    JSONObject task = (JSONObject) parser.parse(completedTask.toString());
                                    if (Objects.equals(slaveID, task.get("slave_id").toString())) {
                                        directory = executor.get("directory").toString();
                                    }
                                }
                            }
                        }
                    }
                }
                i--;
                Thread.sleep(1000);
            }
        } catch (UnirestException | InterruptedException | ParseException e) {
            e.printStackTrace();
        }

        return directory;
    }

}
