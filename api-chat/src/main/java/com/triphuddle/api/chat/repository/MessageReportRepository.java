package com.triphuddle.api.chat.repository;

import java.util.List;
import java.time.Instant;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.triphuddle.api.chat.entity.MessageReport;
import com.triphuddle.api.chat.entity.ReportStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link MessageReport}.
 */
@Repository
public interface MessageReportRepository extends BaseJpaRepository<MessageReport, UUID> {

    /**
     * Check if a user already has an open report on a message.
     *
     * @param messageId the ID of the message
     * @param reporterId the ID of the reporting user
     * @param status the status to look for
     * @return true if such a report exists
     */
    boolean existsByMessageIdAndReporterIdAndStatus(String messageId, UUID reporterId, ReportStatus status);

    /**
     * Find a page of the reports with a status, oldest first, after a cursor; reports settled meanwhile drop out
     * of the open ones without shifting the pages.
     *
     * @param status the status of the reports
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the creation time of the previous page's last report
     * @param id its ID
     * @param limit how many to take
     * @return the reports
     */
    @Query("""
            select r from MessageReport r where r.status = :status
              and (:isFirst = true or r.createdAt > :at or (r.createdAt = :at and r.id > :id))
            order by r.createdAt asc, r.id asc
            """)
    List<MessageReport> findByStatus(@Param("status") ReportStatus status, @Param("isFirst") boolean isFirst,
                                     @Param("at") Instant at, @Param("id") UUID id, Pageable limit);
}
