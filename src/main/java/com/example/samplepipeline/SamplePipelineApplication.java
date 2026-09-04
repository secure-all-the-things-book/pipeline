package com.example.samplepipeline;

import bootiful.asciidoctor.DocumentPublisher;
import bootiful.asciidoctor.DocumentsPublishedEvent;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.batch.autoconfigure.JobExecutionEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.concurrent.Executor;

@SpringBootApplication
public class SamplePipelineApplication {

	private static final Logger log = LoggerFactory.getLogger(SamplePipelineApplication.class);

	static void main(String[] args) {
		// SpringApplication.run(SamplePipelineApplication.class, args);
		System.exit(SpringApplication.exit(SpringApplication.run(SamplePipelineApplication.class, args)));
	}

	@Bean
	UsernamePasswordCredentialsProvider usernamePasswordCredentialsProvider(@Value("${GIT_USERNAME}") String user,
			@Value("${GIT_PASSWORD}") String pw) {
		return new UsernamePasswordCredentialsProvider(user, pw);
	}

	@Bean
	static BeanFactoryPostProcessor removeBrokenAwsS3DocumentPublisherBean() {
		return beanFactory -> {
			if (beanFactory instanceof DefaultListableBeanFactory registry
					&& registry.containsBeanDefinition("awsS3DocumentPublisher")) {
				registry.removeBeanDefinition("awsS3DocumentPublisher");
			}
		};
	}

	@Bean
	@ConditionalOnProperty(value = "pipeline.job.publishers.s3.enabled", havingValue = "true")
	DocumentPublisher deduplicatingAwsS3DocumentPublisher(S3Client s3,
			@Value("${pipeline.job.publishers.s3.bucket-name}") String bucketName) {
		return new DeduplicatingAwsS3DocumentPublisher(s3, bucketName);
	}

	@Bean
	ApplicationListener<ApplicationReadyEvent> ready(Executor[] executor) {
		return _ -> {
			for (var e : executor)
				log.info(e.toString());
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
