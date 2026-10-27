package com.example.dms.controller;

import com.example.dms.dto.*;
import com.example.dms.exception.ApiException;
import com.example.dms.security.AppUserDetails;
import com.example.dms.service.DocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;
    private final ObjectMapper objectMapper;

    /** multipart/form-data: file (required), title, description, metadata (JSON object string). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String metadata,
                                   @AuthenticationPrincipal AppUserDetails caller) {
        return service.upload(file, title, description, parseMetadata(metadata), caller);
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal AppUserDetails caller) {
        return service.list(q, page, size, caller);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails caller) {
        return service.get(id, caller);
    }

    /** Inline display (browser preview for PDF/images). Owner or admin. */
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails caller) {
        return file(service.open(id, caller), false);
    }

    /** Attachment download. Owner or admin. */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails caller) {
        return file(service.open(id, caller), true);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDocumentRequest req,
                                   @AuthenticationPrincipal AppUserDetails caller) {
        return service.update(id, req, caller);
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse replaceFile(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                        @AuthenticationPrincipal AppUserDetails caller) {
        return service.replaceFile(id, file, caller);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails caller) {
        service.delete(id, caller);
    }

    // ---- helpers

    private ResponseEntity<Resource> file(DocumentFile f, boolean attachment) {
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(f.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(f.resource());
    }

    private Map<String, String> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("'metadata' must be a JSON object of string values, e.g. {\"department\":\"HR\"}");
        }
    }
}
