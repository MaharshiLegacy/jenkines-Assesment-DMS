package com.example.dms.service;

import com.example.dms.config.AppProperties;
import com.example.dms.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploads on local disk. Three independent checks protect against bad files:
 *  1. extension whitelist, 2. magic-number (file signature) check, 3. server-decided Content-Type
 * (the client-supplied Content-Type header is never trusted). Files are stored under a random
 * UUID name, which also rules out path traversal.
 */
@Slf4j
@Service
public class FileStorageService {

    public record StoredFile(String storedName, String originalName, String contentType, long size, String sha256) {}

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", DOCX),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", XLSX),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", PPTX),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"));

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};                                   // %PDF
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};              // legacy Office
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};                                   // OOXML container
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38};                                   // GIF8

    private static final Map<String, byte[]> SIGNATURES = Map.ofEntries(
            Map.entry("pdf", PDF), Map.entry("doc", OLE), Map.entry("xls", OLE), Map.entry("ppt", OLE),
            Map.entry("docx", ZIP), Map.entry("xlsx", ZIP), Map.entry("pptx", ZIP),
            Map.entry("png", PNG), Map.entry("jpg", JPG), Map.entry("jpeg", JPG), Map.entry("gif", GIF));

    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory " + root, e);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file content was provided");
        }
        String original = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(original);
        String contentType = ALLOWED_TYPES.get(ext);
        if (contentType == null) {
            throw ApiException.unsupportedType("File type '" + (ext.isEmpty() ? "(none)" : "." + ext)
                    + "' is not allowed. Allowed: " + String.join(", ", new TreeSet<>(ALLOWED_TYPES.keySet())));
        }
        verifySignature(file, ext);

        String storedName = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("Invalid file name");
        }
        try (DigestInputStream in = new DigestInputStream(file.getInputStream(), sha256())) {
            Files.copy(in, target);
            String hash = HexFormat.of().formatHex(in.getMessageDigest().digest());
            return new StoredFile(storedName, original, contentType, Files.size(target), hash);
        } catch (IOException e) {
            deleteQuietly(storedName);
            log.error("Failed to store upload", e);
            throw ApiException.internal("The file could not be stored");
        }
    }

    public Resource load(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.isReadable(p)) {
            throw ApiException.notFound("The document's file content is not available");
        }
        return new FileSystemResource(p);
    }

    public void deleteQuietly(String storedName) {
        try {
            Path p = root.resolve(storedName).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", storedName, e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void verifySignature(MultipartFile file, String ext) {
        byte[] expected = SIGNATURES.get(ext);
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(expected.length);
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read");
        }
        if (head.length < expected.length || !Arrays.equals(head, expected)) {
            throw ApiException.badRequest("File content does not match its '." + ext + "' extension");
        }
    }

    private static String sanitizeName(String raw) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(raw == null ? "" : raw));
        name = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) throw ApiException.badRequest("The uploaded file has no name");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
