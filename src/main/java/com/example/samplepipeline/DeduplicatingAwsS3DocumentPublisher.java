package com.example.samplepipeline;

import bootiful.asciidoctor.DocumentPublisher;
import bootiful.asciidoctor.files.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DeduplicatingAwsS3DocumentPublisher implements DocumentPublisher {

	private static final Logger log = LoggerFactory.getLogger(DeduplicatingAwsS3DocumentPublisher.class);

	private final S3Client s3;

	private final String bucketName;

	private final String contentType = "binary/octet-stream";

	DeduplicatingAwsS3DocumentPublisher(S3Client s3, String bucketName) {
		this.s3 = s3;
		this.bucketName = bucketName;
	}

	@Override
	public void publish(Map<String, Collection<File>> files) throws Exception {
		var nestedFolder = timestring(new Date());
		log.debug("the time string will be {}", nestedFolder);
		var distinctFiles = flattenPublishedFiles(files);
		if (distinctFiles.isEmpty()) {
			log.warn("No files were produced for S3 publication");
			return;
		}
		var zipFileDir = Files.createTempDirectory(nestedFolder).toFile();
		try {
			var zipFile = new File(zipFileDir, "documents.zip");
			ZipUtils.buildZipFileFromFiles(zipFile, distinctFiles.toArray(File[]::new));
			upload(this.bucketName, this.contentType, nestedFolder, zipFile);
		}
		finally {
			FileSystemUtils.deleteRecursively(zipFileDir);
		}
	}

	static List<File> flattenPublishedFiles(Map<String, Collection<File>> files) {
		var distinctFiles = new LinkedHashMap<String, File>();
		for (var collectionOfFiles : files.values()) {
			for (var file : collectionOfFiles) {
				addDistinctFiles(file, distinctFiles);
			}
		}
		return new ArrayList<>(distinctFiles.values());
	}

	private static void addDistinctFiles(File file, Map<String, File> distinctFiles) {
		if (file == null) {
			return;
		}
		if (file.isFile()) {
			distinctFiles.putIfAbsent(file.toPath().toAbsolutePath().normalize().toString(), file);
		}
		else if (file.isDirectory()) {
			var children = file.listFiles();
			if (children == null) {
				return;
			}
			for (var child : children) {
				addDistinctFiles(child, distinctFiles);
			}
		}
	}

	private String timestring(Date runtime) {
		var format = "yyyy_MM_dd_HH_mm_ss";
		var cal = Calendar.getInstance();
		cal.setTime(runtime);
		var sdf = new SimpleDateFormat(format);
		return sdf.format(cal.getTime());
	}

	private URI upload(String bucketName, String contentType, String nestedBucketFolder, File file) {
		Assert.state(file.length() > 0, "the zip file must not be empty");
		var key = nestedBucketFolder + "/" + file.getName();
		var putObjectRequest = PutObjectRequest.builder()
			.bucket(bucketName)
			.key(key)
			.contentType(contentType)
			.build();
		var putObjectResponse = this.s3.putObject(putObjectRequest, file.toPath());
		Assert.notNull(putObjectResponse, "the S3 file hasn't been uploaded");
		var uri = URI.create("s3://" + bucketName + "/" + key);
		log.info("uploaded the file {} to bucket {} with content type {} and nested folder {}. The resulting URI is {}",
				file.getAbsolutePath(), bucketName, contentType, nestedBucketFolder, uri);
		return uri;
	}

}
