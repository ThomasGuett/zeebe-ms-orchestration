# Idea of this project
this project aims to be a demo that illustrates microservice orchestration via Camundas Zeebe engine (CamundaCloud)
in a common environment:
    - spring:boot applications
    - Docker containers
    - organized in DockerCompose (later Kubernetes)
    - combined with Kafka

for better visualization this demo also includes:
    - integration of Consul (microservice health check: [http://localhost:8500](http://localhost:8500))
    - GoogleDrive integration (authorization required)

### Preparations for demo

# prepare Docker
- provide sufficient resources to your Docker environment (tested with 7GB RAM)

# prepare Cluster access
- create a new cluster at [https://console.cloud.camunda.io](https://console.cloud.camunda.io)
- create new API credentials
- download these credentials and update clusterID, clientID and clientSecret in application.properties

# prepare GoogleDrive access (optional)
- for GoogleDrive REST-api integration:
  - check out: [https://developers.google.com/drive/api/v3/quickstart/java](https://developers.google.com/drive/api/v3/quickstart/java)
  - setup credentials (Desktop Application): [https://developers.google.com/workspace/guides/create-credentials#desktop-app](https://developers.google.com/workspace/guides/create-credentials#desktop-app)
  - follow through this guide and create your credentials.json, save it as ./src/main/resources/credentials-google.json

### Start Demo
to build and run the containers, follow these steps:
(Linux/Mac users can also just run ./setup.sh - CAUTION will wipe unused shut down Docker container, to prevent that step just comment out "docker rm -f $(docker ps -a -q)")
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