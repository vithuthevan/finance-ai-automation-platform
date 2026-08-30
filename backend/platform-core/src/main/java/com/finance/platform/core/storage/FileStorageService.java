package com.finance.platform.core.storage;

import java.io.InputStream;

public interface FileStorageService {

	void store(String storageKey, byte[] content, String contentType);

	InputStream open(String storageKey);

	void delete(String storageKey);
}
