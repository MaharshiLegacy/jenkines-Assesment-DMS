package com.example.dms.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_owner", columnList = "owner_id"),
        @Index(name = "idx_documents_title", columnList = "title")})
@Getter @Setter @NoArgsConstructor
public class Document {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Random UUID-based name on disk: users never control the storage path. */
    @Column(name = "stored_filename", nullable = false, unique = true, length = 100)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<DocumentMetadata> metadata = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /**
     * Merge (not clear + re-add) so Hibernate never inserts a key before the old row is
     * deleted, which would violate the (document_id, meta_key) unique constraint.
     */
    public void replaceMetadata(Map<String, String> incoming) {
        metadata.removeIf(m -> !incoming.containsKey(m.getMetaKey()));
        Map<String, DocumentMetadata> existing = metadata.stream()
                .collect(Collectors.toMap(DocumentMetadata::getMetaKey, Function.identity()));
        incoming.forEach((k, v) -> {
            DocumentMetadata m = existing.get(k);
            if (m != null) m.setMetaValue(v);
            else metadata.add(new DocumentMetadata(this, k, v));
        });
    }
}
