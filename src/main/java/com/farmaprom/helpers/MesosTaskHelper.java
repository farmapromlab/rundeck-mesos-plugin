package com.farmaprom.helpers;

import com.farmaprom.logger.LoggerWrapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Objects;

import static org.apache.mesos.Protos.TaskState.TASK_FINISHED;

public class MesosTaskHelper {


    public static void getMesosTaskOutput(LoggerWrapper loggerWrapper) {
        Integer mesosMasterPort = loggerWrapper.masterInfo.getPort();

        JSONParser parser = new JSONParser();
        try {
            Thread.sleep(2000);

            int ip = loggerWrapper.masterInfo.getIp();
            String mesosMasterIP = String.format("%d.%d.%d.%d",
                    (ip & 0xff),
                    (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff),
                    (ip >> 24 & 0xff));


            String logs = getMesosTaskLogs(parser, loggerWrapper, mesosMasterIP, mesosMasterPort);

            System.out.println("");
            System.out.println(logs);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String getMesosTaskLogs(JSONParser parser, LoggerWrapper loggerWrapper, String mesosMasterIP, Integer mesosMasterPort) {

        String executeFrameworkID = loggerWrapper.frameworkID.getValue();
        String slaveID = loggerWrapper.task.getSlaveId().getValue();
        String taskID = loggerWrapper.task.getTaskId().getValue();
        String slaveHostName = "";

        String directory = "";

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

            return getLogFileData(loggerWrapper, slaveHostName, directory);
        } catch (UnirestException | ParseException e) {
            e.printStackTrace();
        }

        return "";
    }

    private static String getLogFileData(LoggerWrapper loggerWrapper, String slaveHostName, String directory) {
        JSONParser parser = new JSONParser();

        String file = "stdout";
        if (TASK_FINISHED != loggerWrapper.taskStatus.getState()) {
            file = "stderr";
        }

        HttpResponse<JsonNode> jsonResponse;
        int status, numAttempts = 0;

        try {
            do {
                jsonResponse = Unirest.get("http://" + slaveHostName + ":5051/files/read.json")
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

            return fileRead.get("data").toString();
        } catch (UnirestException | ParseException | InterruptedException e) {
            e.printStackTrace();
        }

        return "";
    }
}
