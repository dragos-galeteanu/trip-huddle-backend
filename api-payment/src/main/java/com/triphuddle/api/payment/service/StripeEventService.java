package com.triphuddle.api.payment.service;

import java.util.List;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.api.payment.dto.StripeEventFilter;
import com.triphuddle.api.payment.dto.StripeEventResponse;
import com.triphuddle.api.payment.entity.StripeEvent;
import com.triphuddle.api.payment.entity.StripeEventStatus;
import com.triphuddle.api.payment.repository.StripeEventRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Staff reads of the stored Stripe events, and replaying the ones that failed or were ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StripeEventService {

    private final StripeEventRepository stripeEventRepository;

    /**
     * Get the stored events, newest first, without their payloads.
     *
     * @param filter the cursor, page size and filters
     * @return a page of events
     */
    public CursorPaginated<StripeEventResponse> getAll(StripeEventFilter filter) {
        String eventType = filter.getEventType() == null || filter.getEventType().isBlank()
                ? null : filter.getEventType().trim();
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<StripeEvent> found = stripeEventRepository.findForStaff(filter.getStatus(), eventType, isFirst,
                isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), event -> PageCursor.of(event.getStripeCreatedAt(),
                event.getId()), events -> events.stream().map(event -> toResponse(event, false)).toList());
    }

    /**
     * Get a stored event with its payload.
     *
     * @param id the ID of the stored event
     * @return the event
     */
    public StripeEventResponse getById(UUID id) {
        return toResponse(find(id), true);
    }

    /**
     * Queue a failed or ignored event to be handled again from its first attempt.
     *
     * @param id the ID of the stored event
     * @return the queued event
     */
    @Transactional
    public StripeEventResponse replay(UUID id) {
        StripeEvent event = find(id);
        if (event.getStatus() != StripeEventStatus.FAILED && event.getStatus() != StripeEventStatus.IGNORED) {
            throw new IllegalStateException("Only failed or ignored events can be replayed, this one is %s"
                    .formatted(event.getStatus()));
        }
        event.setStatus(StripeEventStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setProcessedAt(null);
        log.info("Stripe event queued for replay [stripeEventId={}, type={}]", event.getStripeEventId(),
                event.getEventType());
        return toResponse(event, false);
    }

    private StripeEvent find(UUID id) {
        return stripeEventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stripe event with id=%s not found".formatted(id)));
    }

    private static StripeEventResponse toResponse(StripeEvent event, boolean isWithPayload) {
        return new StripeEventResponse(event.getId(), event.getStripeEventId(), event.getEventType(),
                event.getApiVersion(), event.isLivemode(), event.getStripeCreatedAt(), event.getCreatedAt(),
                event.getStatus(), event.getAttempts(), event.getNextAttemptAt(), event.getProcessedAt(),
                event.getLastError(), isWithPayload ? event.getPayload() : null);
    }
}
