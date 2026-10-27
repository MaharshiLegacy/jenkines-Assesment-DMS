package com.example.dms.repository;

import com.example.dms.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    boolean existsByOwnerId(Long ownerId);

    /** A normal user's documents only (the ones they uploaded), filtered by title/filename/metadata value. */
    @Query("""
            select d from Document d
            where d.owner.id = :ownerId
              and (lower(d.title) like lower(concat('%', :q, '%'))
                   or lower(d.originalFilename) like lower(concat('%', :q, '%'))
                   or exists (select 1 from DocumentMetadata m
                              where m.document = d and lower(m.metaValue) like lower(concat('%', :q, '%'))))
            """)
    Page<Document> searchByOwner(@Param("ownerId") Long ownerId, @Param("q") String q, Pageable pageable);

    /** Admin view: every document. */
    @Query("""
            select d from Document d
            where lower(d.title) like lower(concat('%', :q, '%'))
               or lower(d.originalFilename) like lower(concat('%', :q, '%'))
               or exists (select 1 from DocumentMetadata m
                          where m.document = d and lower(m.metaValue) like lower(concat('%', :q, '%')))
            """)
    Page<Document> searchAll(@Param("q") String q, Pageable pageable);
}
