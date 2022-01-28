package com.thomasguett.demo;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.FileContent;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.ZeebeClientLifecycle;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableZeebeClient
@RestController
public class DemoApplication {

	@Autowired
	private ZeebeClientLifecycle client;

	/* GoogleDrive */
	private static final String CREDENTIALS_FILE_PATH = "/credentials-desktop.json";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
	private static final String TOKENS_DIRECTORY_PATH = "tokens";
	private static final String APPLICATION_NAME = "DemoProject";
	LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9081).build();
	private String GOOGLE_DRIVE_AUTH_URL = ""; // placeholder for auth requests
	private static final String LOCALHOSTANDPORT = "http://localhost:8090";
	private static final String GOOGLE_ACCOUNT_AUTH_URL = "https://accounts.google.com";

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Value("${workerType:}")
	private String postfix;
	@Value("${driveWorkerType:}")
	private String driveWorkerType;
	@Value("${deeplAuthKey:}")
	private String deeplAuthKey;
	@Value("${targetLanguage:EN}")
	private String targetLanguage;

	@ZeebeWorker(type = "${workerType:concatinate1}")
	public void handleConcatination(final JobClient client, final ActivatedJob job) {
		Map<String,Object> variables = job.getVariablesAsMap();
		String content = (String) variables.getOrDefault("content", "");
		variables.put("content", content + "_" + postfix);

		client.newCompleteCommand(job.getKey())
				.variables(variables)
				.send()
				.whenComplete((result, exception) -> {
					if(exception == null) {
						System.out.println("completed job successfully: " + postfix);
					} else {
						System.out.println("failed to complete job: " + exception);
					}
				});
	}

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
				fileMetadata.setName("test.txt");
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
						System.out.println("completed job successfully: " + postfix);
					} else {
						System.out.println("failed to complete job: " + exception);
					}
				});
	}

	@GetMapping("/status")
	public String getStatus() {
		Topology topology = client.newTopologyRequest().send().join();
		return topology.toString();
	}

	@GetMapping(value = "/start/{value}")
	public String startWorkflowInstance(@PathVariable String value) {
		Long instanceKey = client
				.newCreateInstanceCommand()
				.bpmnProcessId("brewCoffeeProcess")
				.latestVersion()
				.variables("{\"content\":\"" + value + "\"}")
				.send()
				.join().getProcessInstanceKey();
		return instanceKey.toString();
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
			throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
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

	private String translateText(String content) throws IOException, InterruptedException {
		String urlEncodedText = URLEncoder.encode(content, StandardCharsets.UTF_8);
		String urlEncodedAuthKey = URLEncoder.encode(deeplAuthKey, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api-free.deepl.com/v2/translate"))
				.header("User-Agent", "Insomnia Test")
				.header("Accept", "*/*")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.method("POST", HttpRequest.BodyPublishers.ofString("text=" + urlEncodedText + "&target_lang=" + targetLanguage + "&auth_key=" + urlEncodedAuthKey))
				.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		JSONObject jsonResponse = new JSONObject(response.body());
		JSONArray jsonTranslations = jsonResponse.optJSONArray("translations");
		JSONObject jsonTranslation = null != jsonTranslations ? jsonTranslations.getJSONObject(0) : new JSONObject();
		return jsonTranslation.optString("text", content);
	}
}
