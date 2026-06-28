package com.example.samplepipeline;

import bootiful.asciidoctor.DocumentsPublishedEvent;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.concurrent.Executor;

@SpringBootApplication
public class SamplePipelineApplication {

	private static final Logger log = LoggerFactory.getLogger(SamplePipelineApplication.class);

	static void main(String[] args) {
		SpringApplication.run(SamplePipelineApplication.class, args);
	}

	@Bean
	UsernamePasswordCredentialsProvider usernamePasswordCredentialsProvider(@Value("${GIT_USERNAME}") String user,
			@Value("${GIT_PASSWORD}") String pw) {
		return new UsernamePasswordCredentialsProvider(user, pw);
	}

	@Bean
	ApplicationListener<ApplicationReadyEvent> ready(Executor[] executor) {
		return _ -> {
			for (var e : executor)
				IO.println(e.toString());
		};
	}

	@Bean
	ApplicationListener<DocumentsPublishedEvent> documentsPublishedListener() {
		return event -> {
			log.info("Ding! The files are ready!");
			event.getSource().forEach((key, value) -> log.info("published {}={}", key, value));
		};
	}

	@Bean
	ApplicationListener<ApplicationReadyEvent> applicationReadyListener(Environment environment) {
		return _ -> List.of("pipeline.job.root", "publication.root", "publication.code")
				.forEach(propertyName -> log.info("{}={}", propertyName, environment.getProperty(propertyName)));
	}

	@Bean
	ApplicationListener<JobExecutionEvent> batchJobListener() {
		return event -> {
			var jobExecution = event.getJobExecution();
			var createTime = jobExecution.getCreateTime();
			var endTime = jobExecution.getEndTime();
			var jobName = jobExecution.getJobInstance().getJobName();
			log.info("job ({}) start time: {}", jobName, createTime);
			log.info("job ({}) stop time: {}", jobName, endTime);
		};
	}

}
