package com.thomasguett.demo;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.ZeebeClientLifecycle;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

	@GetMapping("/actuator/health")
	public String getHealth() {
		return "OK";
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
