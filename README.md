# rundeck-mesos-plugin
------------------------------------
[Rundeck](http://rundeck.org) plugin running jobs in Docker containers on Mesos cluster.

## Features
------------------------------------
- Docker containers support
- Easy configuration 
- Private registry support
- ENV variables
- Mesos framework authentication
- Task log

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

In order for using this plugin, you must provide Mesos native library either by copying compiled libmesos.so to `/usr/lib/` directory, or by installing Mesos together with Rundeck

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
 
## TODO
 - Mounting volumes in Docker containers
 - KILL job
 - Randomizing TASK ID's

## WHY?

 Rundeck > Chronos
