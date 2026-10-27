package com.example.dms.dto;

import org.springframework.core.io.Resource;

public record DocumentFile(Resource resource, String filename, String contentType, long size) {}
