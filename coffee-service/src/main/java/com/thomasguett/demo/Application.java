package com.thomasguett.demo;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
@EnableZeebeClient
@RestController
public class Application {
    @Value("${jobType:}")
    private String jobType;

    public static void main(String[] args) { SpringApplication.run(Application.class, args);}

    @ZeebeWorker(type = "${jobType:coffee-service}")
    public void handleJob(final JobClient client, final ActivatedJob job) {
        Map<String,Object> variables = job.getVariablesAsMap();
        String content = (String) variables.getOrDefault("content", "");
        variables.put("content", content + "_" + jobType);

        if(null != job.getVariablesAsMap().getOrDefault("error", null)) {
            throw new RuntimeException("I'm a tea maker now");
        }

        client.newCompleteCommand(job.getKey())
                .variables(variables)
                .send()
                .whenComplete((result, exception) -> {
                    if(exception == null) {
                        System.out.println("completed job successfully: " + jobType);
                    } else {
                        System.out.println("failed to complete job: " + exception);
                    }
                });
    }
}
