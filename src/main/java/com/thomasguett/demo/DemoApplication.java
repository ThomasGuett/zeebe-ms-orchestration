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
import org.springframework.web.bind.annotation.*;

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

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Value("${workerType:}")
	private String postfix;
	@Value("${deeplAuthKey:}")
	private String deeplAuthKey;
	@Value("${targetLanguage:EN}")
	private String targetLanguage;

	@ZeebeWorker(type = "${workerType:handleConcatination}")
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

	@PostMapping(value = "/startInstance")
	public String startWorkflowInstance(
			@RequestParam(name = "kafkaTopicName", required = false) String topicName,
			@RequestParam(name = "kafkaKey", required = false) String kafkaKey
	) {
		JSONObject jsonInstanceContent = new JSONObject();
		if(null != topicName && !topicName.isBlank()
		&& null != kafkaKey && !kafkaKey.isBlank()) {
			jsonInstanceContent.put("name", topicName);
			jsonInstanceContent.put("key", kafkaKey);
			jsonInstanceContent.put("ttl", 10000);
			jsonInstanceContent.put("payload", new JSONObject());
		}

		long instanceKey = client
				.newCreateInstanceCommand()
				.bpmnProcessId("brewCoffeeProcess")
				.latestVersion()
				.variables(jsonInstanceContent.toString())
				.send()
				.join().getProcessInstanceKey();
		return Long.toString(instanceKey);
	}
}
