package com.thomasguett.demo.setup;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.thomasguett.demo.DemoApplication;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SetupController {

    /* general setup */
    @Value("${zeebe.client.cloud.clusterId:}")
    private String clusterID;
    @Value("${zeebe.client.cloud.clientId:}")
    private String clientID;
    @Value("${zeebe.client.cloud.clientSecret:}")
    private String clientSecret;
    private static final String WELCOME_HTML = "/welcome.html";

    /* GoogleDrive */
    private static final String CREDENTIALS_FILE_PATH = "/credentials-google.json";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String APPLICATION_NAME = "DemoProject";
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9081).build();
    private String GOOGLE_DRIVE_AUTH_URL = ""; // placeholder for auth requests
    private static final String LOCALHOSTANDPORT = "http://localhost:8090";
    private static final String GOOGLE_ACCOUNT_AUTH_URL = "https://accounts.google.com";

    /* Kafka Connection */
    private static final String KAFKA_CONNECTORS_URL = "http://connect:8083/connectors";
    private static final String KAFKA_SOURCE_BODY_PATH = "/ZeebeSourceConnectorBody.json";
    private static final String KAFKA_SINK_BODY_PATH = "/ZeebeSinkConnectorBody.json";

    @Value("${driveWorkerType:}")
    private String driveWorkerType;

    @ZeebeWorker(type = "${driveWorkerType:WriteToGoogle}")
    public void writeToGoogleDrive(final JobClient client, final ActivatedJob job) throws IOException, GeneralSecurityException {
        Map<String,Object> variables = job.getVariablesAsMap();
        String content = (String) variables.getOrDefault("content", "");
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(HTTP_TRANSPORT);
        if(null != credential) {

            Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            FileList searchResult = service.files().list()
                    .setQ("name = 'Demo'")
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name)")
                    .execute();
            List<File> files = searchResult.getFiles();
            if (null == files || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                String fileName = "ImportFromCamunda.txt";

                String folderId = files.get(0).getId();
                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));
                java.io.File binaryFile = new java.io.File(fileName);
                binaryFile.createNewFile();
                FileWriter fileWriter = new FileWriter(fileName);
                fileWriter.write(content);
                fileWriter.close();

                FileContent mediaContent = new FileContent("text/plain", binaryFile);
                File file = service.files().create(fileMetadata, mediaContent)
                        .setFields("id, parents")
                        .execute();
                variables.put("googleDriveId", file.getId());
            }
        }

        client.newCompleteCommand(job.getKey())
                .variables(variables)
                .send()
                .whenComplete((result, exception) -> {
                    if(exception == null) {
                        System.out.println("completed job successfully: " + driveWorkerType);
                    } else {
                        System.out.println("failed to complete job: " + exception);
                    }
                });
    }

    /**
     * shows healthy status in Consul
     * @return
     */
    @GetMapping("/actuator/health")
    public String getHealth() {
        return "OK";
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = DemoApplication.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (null == in) {
            throw new FileNotFoundException("GoogleDrive credentials not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        /* authorize user */
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        Credential credential = flow.loadCredential("user");
        if (credential == null || credential.getRefreshToken() == null && credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60L) {
            String redirectUri = this.receiver.getRedirectUri();
            AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri);
            GOOGLE_DRIVE_AUTH_URL = authorizationUrl.buildRelativeUrl().replace(LOCALHOSTANDPORT,GOOGLE_ACCOUNT_AUTH_URL);
            Thread codeReceiver = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String code = receiver.waitForCode();
                        TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
                        flow.createAndStoreCredential(response, "user");
                    } catch(Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            });
            codeReceiver.start();
        }
        return credential;
    }

    @GetMapping("/authorizegoogledrive")
    public String authorizeGoogleDrive() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(HTTP_TRANSPORT);
        if (null == credential) {
            String driveAuthURL = GOOGLE_DRIVE_AUTH_URL;
            System.out.println("open link in browser to authenticate: " + driveAuthURL);
            StringBuilder authLink = new StringBuilder("<html><head></head><body><a href=\"");
            if(driveAuthURL.startsWith("/o")) {
                driveAuthURL = GOOGLE_ACCOUNT_AUTH_URL + driveAuthURL;
            } else if(driveAuthURL.startsWith(LOCALHOSTANDPORT)) {
                driveAuthURL = driveAuthURL.replaceFirst(LOCALHOSTANDPORT, GOOGLE_ACCOUNT_AUTH_URL);
            }
            authLink.append(driveAuthURL)
                    .append("\">click here to allow the demo access to your GoogleDrive</a>");
            return authLink.toString();
        }
        return "already authorized";
    }



    @GetMapping("/checkdrive/")
    public String writeToGoogleDrive() throws IOException, GeneralSecurityException {
        final StringBuilder driveContents = new StringBuilder();
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(HTTP_TRANSPORT);
        if(null != credential) {

            Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            FileList searchResult = service.files().list()
                    .setQ("name = 'Demo'")
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name)")
                    .execute();
            List<File> files = searchResult.getFiles();
            if (null == files || files.isEmpty()) {
                System.out.println("No files found.");
                driveContents.append("No files found.");
            } else {
                String folderId = files.get(0).getId();

                FileList demoContents = service.files().list()
                        .setQ("'" + folderId + "' in parents")
                        .setPageSize(10)
                        .setFields("nextPageToken, files(id, name)")
                        .execute();

                driveContents.append("Demo contents:\n");

                for (File file : demoContents.getFiles()) {
                    System.out.printf("%s\n", file.getName());
                    driveContents.append(file.getName())
                            .append("\n");
                }

                File fileMetadata = new File();
                fileMetadata.setName("test.txt");
                fileMetadata.setParents(Collections.singletonList(folderId));
                java.io.File binaryFile = new java.io.File("temp.txt");
                binaryFile.createNewFile();
                FileWriter fileWriter = new FileWriter("temp.txt");
                fileWriter.write("test content");
                fileWriter.close();

                FileContent mediaContent = new FileContent("text/plain", binaryFile);
                File file = service.files().create(fileMetadata, mediaContent)
                        .setFields("id, parents")
                        .execute();
                driveContents.append("created new text File");
            }
            return driveContents.toString();
        } else {
            return authorizeGoogleDrive();
        }
    }

    @PostMapping("/createZeebeSinkConnector")
    public String createZeebeSinkConnector(@RequestParam("name") String name) {
        return createKafkaConnector(name, KafkaConnectorType.SINK);
    }

    @PostMapping("/createZeebeSourceConnector")
    public String createZeebeSourceConnector(@RequestParam("name") String name) {
        return createKafkaConnector(name, KafkaConnectorType.SOURCE);
    }

    private String createKafkaConnector(final String name, final KafkaConnectorType connectorType) {
        String successMessage = "creation failed";
        final String jsonConfigFilePath;
        if (connectorType == KafkaConnectorType.SINK) {
            jsonConfigFilePath = KAFKA_SINK_BODY_PATH;
        } else {
            jsonConfigFilePath = KAFKA_SOURCE_BODY_PATH;
        }
        try( InputStream in = DemoApplication.class.getResourceAsStream(jsonConfigFilePath)) {
            if (null == in) {
                throw new FileNotFoundException("JSON file not found: " + jsonConfigFilePath);
            }
            String stringBody = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
            JSONObject jsonSinkBody = new JSONObject(stringBody);
            JSONObject sinkConfig = jsonSinkBody.getJSONObject("config");
            if(null == sinkConfig) {
                throw new RuntimeException("json file missing config, might be corrupted");
            }
            if(connectorType == KafkaConnectorType.SINK) {
                sinkConfig.put("topics",name);
            } else {
                sinkConfig.put("job.types", name);
            }

            sinkConfig.put("zeebe.client.cloud.clusterId", clusterID);
            sinkConfig.put("zeebe.client.cloud.clientId", clientID);
            sinkConfig.put("zeebe.client.cloud.clientSecret", clientSecret);
            jsonSinkBody.put("config", sinkConfig);
            jsonSinkBody.put("name", name);

            CloseableHttpClient httpClient = HttpClients.createDefault();
            StringEntity stringEntity = new StringEntity(jsonSinkBody.toString(), ContentType.APPLICATION_JSON);
            HttpPost httpPost = new HttpPost(KAFKA_CONNECTORS_URL);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(stringEntity);
            HttpResponse response = httpClient.execute(httpPost);
            httpClient.close();
            System.out.println("Kafka responded with Status: " + response.getStatusLine());
            successMessage = connectorType == KafkaConnectorType.SINK ? "created sink" : "created source";
        } catch (IOException e) {
            e.printStackTrace();
        }
        return successMessage;
    }

    @GetMapping("/")
    public String getWelcomePage() {
        String htmlContent = "unable to load html page";
        try( InputStream in = DemoApplication.class.getResourceAsStream(WELCOME_HTML)) {
            if (null == in) {
                throw new FileNotFoundException("unable to load html file: " + WELCOME_HTML);
            }
            htmlContent = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return htmlContent;
    }
}
