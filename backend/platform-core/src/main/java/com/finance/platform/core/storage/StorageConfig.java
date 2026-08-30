package com.finance.platform.core.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	@Bean
	FileStorageService fileStorageService(StorageProperties properties) {
		if ("s3".equalsIgnoreCase(properties.getProvider())) {
			return S3FileStorageService.from(properties.getS3());
		}
		Path root = Path.of(properties.getLocal().getRoot()).toAbsolutePath().normalize();
		return new LocalFileStorageService(root);
	}
}
