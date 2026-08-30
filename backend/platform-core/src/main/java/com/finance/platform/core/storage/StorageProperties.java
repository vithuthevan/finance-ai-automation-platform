package com.finance.platform.core.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

	/**
	 * Active provider: {@code local} or {@code s3}.
	 */
	private String provider = "local";

	private int maxFileSizeMb = 0;

	@Getter(lombok.AccessLevel.NONE)
	private long maxFileSizeBytes = 15 * 1024 * 1024L;

	public long getMaxFileSizeBytes() {
		if (maxFileSizeMb > 0) {
			return maxFileSizeMb * 1024L * 1024L;
		}
		return maxFileSizeBytes;
	}

	private final Local local = new Local();
	private final S3 s3 = new S3();

	@Getter
	@Setter
	public static class Local {
		private String root = "./data/storage";
	}

	@Getter
	@Setter
	public static class S3 {
		private String bucket;
		private String region = "ap-south-1";
		private String endpoint;
		private String accessKey;
		private String secretKey;
	}
}
