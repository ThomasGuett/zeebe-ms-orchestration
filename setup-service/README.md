# Idea of this project
this project aims to be a demo that illustrates microservice orchestration via Camundas Zeebe engine (CamundaCloud)
in a common environment:
    - spring:boot applications
    - Docker containers
    - organized in DockerCompose (later Kubernetes)
    - combined with Kafka

for better visualization this demo also includes:
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
(Linux/Mac users can also just run ./setup.sh - CAUTION will wipe unused shut down Docker container, to prevent that 
step just comment out "docker rm -f $(docker ps -a -q)")
# enter cloud credentials
in ../demo/src/main/resources/application.properties

# create .jar file
mvn clean package

# build docker image
docker build -t camunda/demo-microservice .

# start docker compose
cd into ../demo/src/main/docker/
docker-compose up

# convenient Setup page
open [http://localhost:8090](http://localhost:8090) to find an ugly but convenient demo setup page
    - it allows for GDrive authorization
    - Kafka Sink/Source creation
    - Start of new Process Instances

# authorize google drive
open [http://localhost:8090/authorizegoogledrive](http://localhost:8090/authorizegoogledrive)

# check Kafka cluster health
open [http://localhost:9021](http://localhost:9021/)

# actual doings in the demo
login to [https://console.cloud.camunda.io](https://console.cloud.camunda.io), open your Diagrams and make sure you
uploaded the included "brewCoffeeProcess", open it select "Save and Deploy" and start your first instance

if everything was set up correctly you should now be able to open your freshly created process instance and see it
follow through your microservices (might already be finished)

since there isn't really much happening in the services you should end up with a variable "content" that contains a 
String like "_place-cup_grind-coffee_pour-water" (depends on the order of the microservices)

if you encounter errors or do not see something like that, be sure to check the following things:
- make sure you built the project (mvn clean package)
- make sure startup of Docker-Compose finished (this might take a while - somewhere in the region of 5 minutes)
- check for errors in Docker-Compose log stream (Warnings can be ignored)
- double-check the CamundaCloud API credentials in your application.properties (no brackets " required)
- make sure to deploy the BPMN process to the cluster (not automatically deployed)