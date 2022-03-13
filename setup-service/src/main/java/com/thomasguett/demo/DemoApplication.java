package com.thomasguett.demo;

import com.thomasguett.demo.objects.ProcessStart;
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
@CrossOrigin("*")
public class DemoApplication {

	@Autowired
	private ZeebeClientLifecycle client;

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@PostMapping(value = "/startInstance")
	public String startWorkflowInstance(
			@RequestBody ProcessStart processStart
			) {
		JSONObject jsonInstanceContent = new JSONObject();
		if(null != processStart) {
			if(null != processStart.getKafkaTopicName() && !processStart.getKafkaTopicName().isBlank()
					&& null != processStart.getKafkaKey() && !processStart.getKafkaKey().isBlank()) {
				jsonInstanceContent.put("name", processStart.getKafkaTopicName());
				jsonInstanceContent.put("key", processStart.getKafkaKey());
				jsonInstanceContent.put("ttl", 10000);
				jsonInstanceContent.put("payload", new JSONObject());
			}
		}

		if(null != processStart.getDirection() && !processStart.getDirection().isBlank()) {
			jsonInstanceContent.put("direction", processStart.getDirection());
		}

		if(null != processStart.getName() && !processStart.getName().isBlank()) {
			jsonInstanceContent.put("coffee_recipient", processStart.getName());
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
