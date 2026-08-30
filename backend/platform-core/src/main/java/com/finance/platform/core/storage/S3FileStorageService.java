package com.finance.platform.core.storage;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

	private final S3Client client;
	private final String bucket;

	public static S3FileStorageService from(StorageProperties.S3 properties) {
		if (properties.getBucket() == null || properties.getBucket().isBlank()) {
			throw new IllegalStateException("app.storage.s3.bucket is required when storage.provider=s3");
		}
		var builder = S3Client.builder()
				.region(Region.of(properties.getRegion() == null ? "ap-south-1" : properties.getRegion()));
		if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
			builder.endpointOverride(URI.create(properties.getEndpoint()))
					.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
		}
		if (properties.getAccessKey() != null && properties.getSecretKey() != null) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
		}
		return new S3FileStorageService(builder.build(), properties.getBucket());
	}

	@Override
	public void store(String storageKey, byte[] content, String contentType) {
		assertSafeKey(storageKey);
		try {
			client.putObject(
					PutObjectRequest.builder()
							.bucket(bucket)
							.key(storageKey)
							.contentType(contentType)
							.build(),
					RequestBody.fromBytes(content)
			);
		} catch (RuntimeException ex) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Unable to store file");
		}
	}

	@Override
	public InputStream open(String storageKey) {
		assertSafeKey(storageKey);
		try {
			return client.getObject(GetObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build());
		} catch (RuntimeException ex) {
			String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
			if (message.contains("nosuchkey") || message.contains("404") || message.contains("not found")) {
				throw new BusinessException(ErrorCodes.DOCUMENT_OBJECT_MISSING, "Stored document object is missing");
			}
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Unable to read stored file");
		}
	}

	@Override
	public void delete(String storageKey) {
		assertSafeKey(storageKey);
		try {
			client.deleteObject(DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build());
		} catch (RuntimeException ex) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Unable to delete stored file");
		}
	}

	private static void assertSafeKey(String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
				|| storageKey.startsWith("/") || storageKey.startsWith("\\")) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Invalid storage key");
		}
	}
}
