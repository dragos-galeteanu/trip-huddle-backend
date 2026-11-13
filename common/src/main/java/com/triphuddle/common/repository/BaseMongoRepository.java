package com.triphuddle.common.repository;

import com.triphuddle.common.entity.BaseMongoDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * Adds lookups that leave out soft-deleted and/or disabled records.
 */
@NoRepositoryBean
public interface BaseMongoRepository<T extends BaseMongoDocument, ID> extends MongoRepository<T, ID> {

    /**
     * Find a record by ID that is neither deleted nor disabled.
     *
     * @param id the ID of the record
     * @return the record, or empty if it is missing, deleted or disabled
     */
    Optional<T> findByIdAndDeletedAtIsNullAndDisabledAtIsNull(ID id);

    /**
     * Find a record by ID that is not deleted; it may be disabled.
     *
     * @param id the ID of the record
     * @return the record, or empty if it is missing or deleted
     */
    Optional<T> findByIdAndDeletedAtIsNull(ID id);

    /**
     * Find a record by ID that is not disabled; it may be deleted.
     *
     * @param id the ID of the record
     * @return the record, or empty if it is missing or disabled
     */
    Optional<T> findByIdAndDisabledAtIsNull(ID id);

    /**
     * Find the records that are neither deleted nor disabled.
     *
     * @param pageable the page to return
     * @return a page of the matching records
     */
    Page<T> findAllByDeletedAtIsNullAndDisabledAtIsNull(Pageable pageable);

    /**
     * Find the records that are not deleted; they may be disabled.
     *
     * @param pageable the page to return
     * @return a page of the matching records
     */
    Page<T> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * Find the records that are not disabled; they may be deleted.
     *
     * @param pageable the page to return
     * @return a page of the matching records
     */
    Page<T> findAllByDisabledAtIsNull(Pageable pageable);
}
