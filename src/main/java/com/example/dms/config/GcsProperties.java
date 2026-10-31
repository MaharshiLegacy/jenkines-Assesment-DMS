package com.example.dms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.gcs")
public record GcsProperties(String bucketName) {
}
