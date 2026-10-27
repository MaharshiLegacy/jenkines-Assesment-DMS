package com.example.dms.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Free-form key/value tags (department, year, category ...) attached to a document. */
@Entity
@Table(name = "document_metadata",
        uniqueConstraints = @UniqueConstraint(name = "uk_metadata_doc_key", columnNames = {"document_id", "meta_key"}))
@Getter @Setter @NoArgsConstructor
public class DocumentMetadata {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "meta_key", nullable = false, length = 100)
    private String metaKey;

    @Column(name = "meta_value", nullable = false, length = 500)
    private String metaValue;

    public DocumentMetadata(Document document, String key, String value) {
        this.document = document;
        this.metaKey = key;
        this.metaValue = value;
    }
}
