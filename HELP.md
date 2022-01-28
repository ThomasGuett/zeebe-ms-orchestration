# enter cloud credentials
in ../demo/src/main/resources/application.properties

# create .jar file
mvn clean package

# build docker image
docker build -t camunda/demo-microservice .

# start docker compose
cd into ../demo/src/main/docker/
docker-compose up

# authorize google drive
open [http://localhost:8090/authorizegoogledrive](http://localhost:8090/authorizegoogledrive)

# check up-status of microservices (grind coffee, etc. ) - consul
open [http://localhost:8500](http://localhost:9021/)

# check Kafka cluster health
open [http://localhost:9021](http://localhost:9021/)