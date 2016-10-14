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
            
        gradle build
 3) Put `rundeck-mesos-plugin-*.jar` in Rundeck's plugin directory

## Configuration
------------------------------------

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
 
## TODO
 - volumes 
