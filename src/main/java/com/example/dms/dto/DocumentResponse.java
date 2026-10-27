package com.example.dms.dto;

import java.time.Instant;
import java.util.Map;

public record DocumentResponse(Long id, String title, String description, String filename, String contentType,
                               long sizeBytes, String checksumSha256, String owner, Map<String, String> metadata,
                               Instant createdAt, Instant updatedAt) {}
