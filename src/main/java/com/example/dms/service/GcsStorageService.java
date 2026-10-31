package com.example.dms.service;

import java.io.IOException;
import java.nio.channels.Channels;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.dms.config.GcsProperties;
import com.example.dms.exception.ApiException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(
	    name = "app.storage.type",
	    havingValue = "gcs"
	)
@Service
public class GcsStorageService implements StorageService {

	private final Storage storage;
	private final String bucketName;

	public GcsStorageService(Storage storage, GcsProperties properties) {
		this.storage = storage;
		this.bucketName = properties.bucketName();
	}

	@Override
	public StoredFile store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("No file content was provided");
		}

		String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

		original = StringUtils.getFilename(original);

		if (!StringUtils.hasText(original)) {
			throw ApiException.badRequest("The uploaded file has no name");
		}

		// Use a server-generated object name.
		String extension = "";
		int dot = original.lastIndexOf('.');
		if (dot >= 0 && dot < original.length() - 1) {
			extension = original.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
		}

		String contentType = detectContentType(extension);

		if (contentType == null) {
			throw ApiException.unsupportedType("Unsupported file extension: " + extension);
		}

		String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
		BlobId blobId = BlobId.of(bucketName, storedName);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
		try (DigestInputStream input = new DigestInputStream(file.getInputStream(), sha256())) {
			storage.createFrom(blobInfo, input);
			String hash = HexFormat.of().formatHex(input.getMessageDigest().digest());
			log.info("File uploaded to GCS: {}", storedName);
			return new StoredFile(storedName, original, contentType, file.getSize(), hash);

		} catch (IOException | RuntimeException e) {
			// Clean up a partially created object if necessary.
			try {
				storage.delete(blobId);
			} catch (RuntimeException cleanupException) {
				log.warn("Could not clean up GCS object {}", storedName, cleanupException);
			}
			log.error("Failed to upload file to GCS", e);
			if (e instanceof ApiException apiException) {
				throw apiException;
			}
			throw ApiException.internal("The file could not be stored");
		}

	}

	@Override
	public Resource load(String storedName) {
		Blob blob = storage.get(BlobId.of(bucketName, storedName));
		if (blob == null) {
			throw ApiException.notFound("The document's file content is not available");
		}
		try {
			return new InputStreamResource(Channels.newInputStream(blob.reader()));
		} catch (RuntimeException e) {
			log.error("Failed to read GCS object {}", storedName, e);
			throw ApiException.internal("The file could not be read");
		}
	}
	@Override
	public void deleteQuietly(String storedName) {
		try {
			storage.delete(BlobId.of(bucketName, storedName));
		} catch (RuntimeException e) {
			log.warn("Could not delete GCS object {}", storedName, e);
		}
	}

	private static String detectContentType(String extension) {
		return switch (extension) {
		case "pdf" -> "application/pdf";
		case "png" -> "image/png";
		case "jpg", "jpeg" -> "image/jpeg";
		case "tif", "tiff" -> "image/tiff";
		default -> null;
		};
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
