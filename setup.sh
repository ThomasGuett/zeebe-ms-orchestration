#!/bin/zsh
mvn clean package
docker build -t camunda/demo-microservice .
docker rm -f $(docker ps -a -q)
docker-compose -f ./src/main/docker/Docker-Compose.yml up
