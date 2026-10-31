package com.example.dms.service;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import com.example.dms.service.StorageService.StoredFile;

public interface StorageService {
	public record StoredFile(String storedName, String originalName, String contentType, long size, String sha256) {}

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
     static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
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

    static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};                                   // %PDF
    static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};              // legacy Office
    static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};                                   // OOXML container
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    static final byte[] GIF = {0x47, 0x49, 0x46, 0x38};                                   // GIF8

    static final Map<String, byte[]> SIGNATURES = Map.ofEntries(
            Map.entry("pdf", PDF), Map.entry("doc", OLE), Map.entry("xls", OLE), Map.entry("ppt", OLE),
            Map.entry("docx", ZIP), Map.entry("xlsx", ZIP), Map.entry("pptx", ZIP),
            Map.entry("png", PNG), Map.entry("jpg", JPG), Map.entry("jpeg", JPG), Map.entry("gif", GIF));

    StoredFile store(MultipartFile file);

    Resource load(String storedName);

    void deleteQuietly(String storedName);
    
    
    
}
