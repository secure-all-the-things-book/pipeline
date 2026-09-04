package com.example.samplepipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeduplicatingAwsS3DocumentPublisherTests {

	@Test
	void flattenPublishedFilesDeduplicatesFilesExpandedFromDuplicateDirectories(@TempDir Path root) throws Exception {
		var images = Files.createDirectories(root.resolve("docs/images"));
		var png = Files.writeString(images.resolve("cover.png"), "image");
		var html = Files.writeString(root.resolve("docs/index.html"), "<html/>");

		var flattened = DeduplicatingAwsS3DocumentPublisher.flattenPublishedFiles(Map.of("MarkdownProducer",
				List.of(images.toFile(), html.toFile()), "HtmlProducer", List.of(images.toFile(), html.toFile())));

		assertEquals(List.of(png.toFile().getAbsolutePath(), html.toFile().getAbsolutePath()).stream().sorted().toList(),
				flattened.stream().map(file -> file.getAbsolutePath()).sorted().toList());
	}

}
