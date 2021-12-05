package com.thomasguett.demo;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.ZeebeClientLifecycle;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
@EnableZeebeClient
@RestController
public class DemoApplication {

	@Autowired
	private ZeebeClientLifecycle client;

	public static void main(String[] args) {
//		new SpringApplicationBuilder(DemoApplication.class).web(WebApplicationType.SERVLET).run(args);
		SpringApplication.run(DemoApplication.class, args);
	}

	@Value("${workerType:}")
	private String postfix;

	private void handleConcatination(final JobClient client, final ActivatedJob job) {
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

	@ZeebeWorker(type = "${workerType:concatinate1}")
	public void handleJobConcatinate(final JobClient client, final ActivatedJob job) {
		handleConcatination(client, job);
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
				.bpmnProcessId("nonsenseProcess")
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
}
