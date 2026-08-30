package com.finance.platform.core.storage;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

	private final Path root;

	@Override
	public void store(String storageKey, byte[] content, String contentType) {
		Path target = resolve(storageKey);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content, StandardOpenOption.CREATE_NEW);
		} catch (IOException ex) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Unable to store file");
		}
	}

	@Override
	public InputStream open(String storageKey) {
		Path target = resolve(storageKey);
		if (!Files.exists(target) || !Files.isRegularFile(target)) {
			throw new BusinessException(ErrorCodes.DOCUMENT_OBJECT_MISSING, "Stored document object is missing");
		}
		try {
			return Files.newInputStream(target, StandardOpenOption.READ);
		} catch (IOException ex) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Unable to read stored file");
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		} catch (IOException ex) {
			log.warn("Unable to delete stored file {}", storageKey);
		}
	}

	private Path resolve(String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
				|| storageKey.startsWith("/") || storageKey.startsWith("\\")
				|| storageKey.contains(":")) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Invalid storage key");
		}
		Path normalized = root.resolve(storageKey.replace('\\', '/')).normalize();
		if (!normalized.startsWith(root)) {
			throw new BusinessException(ErrorCodes.DOCUMENT_STORAGE_FAILED, "Invalid storage key");
		}
		return normalized;
	}
}
