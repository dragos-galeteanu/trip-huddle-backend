package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ContentReport;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link ContentReport}.
 */
@Repository
public interface ContentReportRepository extends BaseJpaRepository<ContentReport, UUID> {

    /**
     * Find a page of reports for staff, the oldest first, after a cursor; reports resolved meanwhile drop out of
     * the open ones without shifting the pages.
     *
     * @param isOpen true for open reports, false for resolved ones
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the creation time of the previous page's last report
     * @param id its ID
     * @param limit how many to take
     * @return the reports
     */
    @Query("""
            select r from ContentReport r join fetch r.content c join fetch c.place
            where ((:isOpen = true and r.resolution is null) or (:isOpen = false and r.resolution is not null))
              and (:isFirst = true or r.createdAt > :at or (r.createdAt = :at and r.id > :id))
            order by r.createdAt asc, r.id asc
            """)
    List<ContentReport> findForStaff(@Param("isOpen") boolean isOpen, @Param("isFirst") boolean isFirst,
                                     @Param("at") Instant at, @Param("id") UUID id, Pageable limit);
}
