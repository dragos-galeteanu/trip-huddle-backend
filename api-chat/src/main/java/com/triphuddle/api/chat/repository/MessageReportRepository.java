package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.MessageReport;
import com.triphuddle.api.chat.entity.ReportStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
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
     * Find the reports with a status, oldest first via the pageable's sort.
     *
     * @param status the status of the reports
     * @param pageable the page to return
     * @return a page of the reports
     */
    Page<MessageReport> findByStatus(ReportStatus status, Pageable pageable);
}
