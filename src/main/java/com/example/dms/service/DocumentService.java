package com.example.dms.service;

import com.example.dms.dto.DocumentFile;
import com.example.dms.dto.DocumentResponse;
import com.example.dms.dto.PageResponse;
import com.example.dms.dto.UpdateDocumentRequest;
import com.example.dms.exception.ApiException;
import com.example.dms.model.Document;
import com.example.dms.repository.DocumentRepository;
import com.example.dms.repository.UserRepository;
import com.example.dms.security.AppUserDetails;
import com.example.dms.service.FileStorageService.StoredFile;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * All document rules live here, so authorization cannot be bypassed by adding a new controller.
 *
 *   USER  -> can only see / download / update / delete the documents THEY uploaded
 *   ADMIN -> can manage every document
 *   anyone else's document -> 403
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int MAX_METADATA_ENTRIES = 20;

    private final DocumentRepository documents;
    private final UserRepository users;
    private final FileStorageService storage;

    // ------------------------------------------------------------------ create

    @Transactional
    public DocumentResponse upload(MultipartFile file, String title, String description,
                                   Map<String, String> metadata, AppUserDetails caller) {
        StoredFile stored = storage.store(file);
        deleteFileOnRollback(stored.storedName());              // no orphan files if the DB write fails

        Document d = new Document();
        d.setTitle(resolveTitle(title, stored.originalName()));
        d.setDescription(validDescription(description));
        applyFile(d, stored);
        d.setOwner(users.getReferenceById(caller.getId()));
        d.replaceMetadata(validMetadata(metadata));
        documents.save(d);

        log.info("User {} uploaded document {} ({})", caller.getUsername(), d.getId(), stored.originalName());
        return toResponse(d, caller.getUsername());
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String q, int page, int size, AppUserDetails caller) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String term = q == null ? "" : q.trim();
        var result = caller.isAdmin()
                ? documents.searchAll(term, pageable)
                : documents.searchByOwner(caller.getId(), term, pageable);
        return PageResponse.of(result.map(d -> toResponse(d, d.getOwner().getUsername())));
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, AppUserDetails caller) {
        Document d = authorize(id, caller);
        return toResponse(d, d.getOwner().getUsername());
    }

    @Transactional(readOnly = true)
    public DocumentFile open(Long id, AppUserDetails caller) {
        Document d = authorize(id, caller);
        log.info("User {} accessed file of document {}", caller.getUsername(), id);
        return new DocumentFile(storage.load(d.getStoredFilename()), d.getOriginalFilename(),
                d.getContentType(), d.getSizeBytes());
    }

    // ------------------------------------------------------------------ update / delete

    @Transactional
    public DocumentResponse update(Long id, UpdateDocumentRequest req, AppUserDetails caller) {
        Document d = authorize(id, caller);
        if (req.title() != null) d.setTitle(resolveTitle(req.title(), d.getOriginalFilename()));
        if (req.description() != null) d.setDescription(validDescription(req.description()));
        if (req.metadata() != null) d.replaceMetadata(validMetadata(req.metadata()));
        d.setUpdatedAt(Instant.now());
        return toResponse(d, d.getOwner().getUsername());
    }

    @Transactional
    public DocumentResponse replaceFile(Long id, MultipartFile file, AppUserDetails caller) {
        Document d = authorize(id, caller);
        StoredFile stored = storage.store(file);
        deleteFileOnRollback(stored.storedName());
        String oldName = d.getStoredFilename();
        applyFile(d, stored);
        d.setUpdatedAt(Instant.now());
        deleteFileAfterCommit(oldName);
        log.info("User {} replaced the file of document {}", caller.getUsername(), id);
        return toResponse(d, d.getOwner().getUsername());
    }

    @Transactional
    public void delete(Long id, AppUserDetails caller) {
        Document d = authorize(id, caller);
        String storedName = d.getStoredFilename();
        documents.delete(d);                                     // cascades to metadata
        deleteFileAfterCommit(storedName);
        log.info("User {} deleted document {}", caller.getUsername(), id);
    }

    // ------------------------------------------------------------------ authorization

    /** 404 if it does not exist, 403 if it exists but the caller is neither its owner nor an admin. */
    private Document authorize(Long id, AppUserDetails caller) {
        Document d = documents.findById(id)
                .orElseThrow(() -> ApiException.notFound("Document " + id + " not found"));
        if (caller.isAdmin() || d.getOwner().getId().equals(caller.getId())) return d;
        log.warn("Access denied: user {} tried to access document {}", caller.getUsername(), id);
        throw new AccessDeniedException("You can only access documents that you uploaded");
    }

    // ------------------------------------------------------------------ helpers

    private static void applyFile(Document d, StoredFile s) {
        d.setOriginalFilename(s.originalName());
        d.setStoredFilename(s.storedName());
        d.setContentType(s.contentType());
        d.setSizeBytes(s.size());
        d.setChecksumSha256(s.sha256());
    }

    private static String resolveTitle(String title, String fallback) {
        String t = (title == null || title.isBlank()) ? fallback : title.trim();
        if (t.length() > 200) throw ApiException.badRequest("Title must be at most 200 characters");
        return t;
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) return null;
        if (description.length() > 1000) throw ApiException.badRequest("Description must be at most 1000 characters");
        return description.trim();
    }

    private static Map<String, String> validMetadata(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        if (in == null) return out;
        if (in.size() > MAX_METADATA_ENTRIES) {
            throw ApiException.badRequest("At most " + MAX_METADATA_ENTRIES + " metadata entries are allowed");
        }
        in.forEach((k, v) -> {
            String key = k == null ? "" : k.trim();
            String value = v == null ? "" : v.trim();
            if (key.isEmpty() || key.length() > 100) throw ApiException.badRequest("Metadata keys must be 1-100 characters");
            if (value.isEmpty() || value.length() > 500) throw ApiException.badRequest("Metadata values must be 1-500 characters");
            out.put(key, value);
        });
        return out;
    }

    private DocumentResponse toResponse(Document d, String ownerUsername) {
        Map<String, String> meta = new TreeMap<>();
        d.getMetadata().forEach(m -> meta.put(m.getMetaKey(), m.getMetaValue()));
        return new DocumentResponse(d.getId(), d.getTitle(), d.getDescription(), d.getOriginalFilename(),
                d.getContentType(), d.getSizeBytes(), d.getChecksumSha256(), ownerUsername,
                meta, d.getCreatedAt(), d.getUpdatedAt());
    }

    /** Keep disk and DB consistent: remove a newly written file if the transaction does not commit. */
    private void deleteFileOnRollback(String storedName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.deleteQuietly(storedName);
            }
        });
    }

    /** Only remove an old/deleted file once the DB change is durably committed. */
    private void deleteFileAfterCommit(String storedName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.deleteQuietly(storedName); }
        });
    }
}
