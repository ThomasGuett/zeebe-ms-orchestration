#!/bin/zsh
# comment out, if you don't want old docker images to be thrown out
docker rm -f $(docker ps -a -q)

cd ./setup-service
mvn clean package
docker build -t camunda/setup-service .

cd ../coffee-service
mvn clean package
docker build -t camunda/demo-microservice .

cd ..
docker-compose -f ./docker/Docker-Compose.yml up
