package com.example.dms.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/** All fields optional: null = leave unchanged. metadata, when present, replaces the full set. */
public record UpdateDocumentRequest(@Size(max = 200) String title,
                                    @Size(max = 1000) String description,
                                    Map<String, String> metadata) {}
