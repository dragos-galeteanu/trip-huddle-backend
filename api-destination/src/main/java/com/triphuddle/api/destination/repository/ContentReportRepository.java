package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ContentReport;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link ContentReport}.
 */
@Repository
public interface ContentReportRepository extends BaseJpaRepository<ContentReport, UUID> {

    /**
     * Find reports for staff, oldest first.
     *
     * @param isOpen true for open reports, false for resolved ones
     * @param pageable the page
     * @return a page of reports
     */
    @Query("""
            select r from ContentReport r join fetch r.content c join fetch c.place
            where (:isOpen = true and r.resolution is null) or (:isOpen = false and r.resolution is not null)
            order by r.createdAt
            """)
    Page<ContentReport> findForStaff(@Param("isOpen") boolean isOpen, Pageable pageable);
}
