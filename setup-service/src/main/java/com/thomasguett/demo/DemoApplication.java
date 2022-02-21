package com.thomasguett.demo;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.ZeebeClientLifecycle;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@EnableZeebeClient
@RestController
public class DemoApplication {

	@Autowired
	private ZeebeClientLifecycle client;

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
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
