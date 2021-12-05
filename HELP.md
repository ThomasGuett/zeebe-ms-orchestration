# enter cloud credentials
in ../demo/src/main/resources/application.properties

# create .jar file
mvn clean package

# build docker image
docker build -t camunda/demo-microservice .

# start docker compose
cd into ../demo/src/main/docker/
docker-compose up