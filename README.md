# rundeck-mesos-plugin
------------------------------------
[Rundeck](http://rundeck.org) plugin for running jobs in Docker containers on Mesos cluster.

[![Build Status](https://travis-ci.org/farmapromlab/rundeck-mesos-plugin.svg?branch=master)](https://travis-ci.org/farmapromlab/rundeck-mesos-plugin)

## Features
------------------------------------
- Docker containers support
- Easy configuration
- Private registry support
- ENV variables
- Mesos framework authentication
- Task log

## Requirements
------------------------------------
- Rundeck 2.6.9+
- Mesos 1.0.1+

## Installation
------------------------------------

#### Building from source
 1) Clone this repo

 2) Build using gradle

        ./gradlew build
 3) Put `rundeck-mesos-plugin-*.jar` in Rundeck's plugin directory

## Configuration
------------------------------------

### Mesos native library

In order for using this plugin, you must provide Mesos native library either by copying compiled libmesos.so to `/usr/lib/` directory, or by installing Mesos together with Rundeck.

**WARNING:** libmesos.so should be compiled specifically for your platform. Please don't mix files compiled on Ubuntu or CentOS with debian platform and conversely. Also restart Rundeck afterwards!

### Mesos TASK user

By default Mesos Agent attempts to run task as the `user` who submitted it. Rundeck runs with user `rundeck` so make sure this user exists on every agent, or run agents with option `--no-switch-user`

### Private registry
 1) Login to you private registry manually

        docker login your.private.registry.com
        Username: yourname
        Password:
        Email: example@example.com
 2) Tar `.docker/config.json` created in your home directory

        cd ~/
        tar czf docker.tar.gz .docker

 3) Put `docker.tar.gz` on every mesos agent that will be running your containers, for example in `/etc/docker.tar.gz`

 4) Specify URIs parameter `file:///etc/docker.tar.gz` in your job definition

## Screenshots
------------------------------------

![Alt Screenshot](https://raw.githubusercontent.com/farmapromlab/rundeck-mesos-plugin/master/screenshots/screen.jpg "Rundeck mesos plugin")

## Constraints

You can define where you want your task to be executed by setting constraints on mesos agents hostnames or other arguments.

![Alt Screenshot](https://raw.githubusercontent.com/farmapromlab/rundeck-mesos-plugin/master/screenshots/constraints.jpg "Constraints")

Operators:
- LIKE
- UNLIKE
- EQUALS

## TODO
- Refactoring
- Test

## WHY?

 Rundeck > Chronos
