package com.triphuddle.api.destination.social;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.api.destination.dto.MarkedPlaceResponse;
import com.triphuddle.api.destination.dto.PlaceMarkResponse;
import com.triphuddle.api.destination.dto.PlaceSummaryResponse;
import com.triphuddle.api.destination.entity.PlaceMark;
import com.triphuddle.api.destination.entity.PlaceMarkKind;
import com.triphuddle.api.destination.repository.PlaceMarkRepository;
import com.triphuddle.api.destination.service.PlaceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps each user's favourite and visited places, which only they see.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlaceMarkService {

    private final PlaceMarkRepository repository;
    private final PlaceQueryService placeQueryService;

    /**
     * Mark a place; marking it again only updates the visit date.
     *
     * @param userId the ID of the user
     * @param placeId the ID of the place
     * @param kind FAVOURITE or VISITED
     * @param visitedOn when they went, for VISITED, or null
     * @return the mark
     */
    public PlaceMarkResponse mark(UUID userId, UUID placeId, PlaceMarkKind kind, LocalDate visitedOn) {
        // A merged place's mark goes on the place it went into.
        UUID target = placeQueryService.visiblePlace(placeId, false).getId();
        PlaceMark mark = repository.findByUserIdAndPlaceIdAndKind(userId, target, kind).orElseGet(() -> {
            PlaceMark created = new PlaceMark();
            created.setUserId(userId);
            created.setPlaceId(target);
            created.setKind(kind);
            return created;
        });
        mark.setVisitedOn(kind == PlaceMarkKind.VISITED ? visitedOn : null);
        return toResponse(repository.save(mark));
    }

    /**
     * Remove a mark; removing one that isn't there is harmless.
     *
     * @param userId the ID of the user
     * @param placeId the ID of the place
     * @param kind FAVOURITE or VISITED
     */
    public void unmark(UUID userId, UUID placeId, PlaceMarkKind kind) {
        repository.findByUserIdAndPlaceIdAndKind(userId, placeId, kind).ifPresent(repository::delete);
    }

    /**
     * Get a user's favourites or visited places, the newest first.
     *
     * @param userId the ID of the user
     * @param kind FAVOURITE or VISITED
     * @param filter the cursor and page size
     * @return a page of places
     */
    @Transactional(readOnly = true)
    public CursorPaginated<MarkedPlaceResponse> getMine(UUID userId, PlaceMarkKind kind,
                                                        CursorPaginationFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<PlaceMark> found = repository.findListed(userId, kind, isFirst,
                isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), mark -> PageCursor.of(mark.getCreatedAt(), mark.getId()),
                marks -> {
            Map<UUID, PlaceSummaryResponse> places = placeQueryService.summariesOf(
                            marks.stream().map(PlaceMark::getPlaceId).toList()).stream()
                    .collect(Collectors.toMap(PlaceSummaryResponse::id, Function.identity()));
            return marks.stream().filter(mark -> places.containsKey(mark.getPlaceId()))
                    .map(mark -> new MarkedPlaceResponse(places.get(mark.getPlaceId()), mark.getVisitedOn(),
                            mark.getCreatedAt()))
                    .toList();
        });
    }

    /**
     * Get a user's marks on some places, for showing them on a list or map.
     *
     * @param userId the ID of the user
     * @param placeIds the IDs of the places
     * @return the marks there are
     */
    @Transactional(readOnly = true)
    public List<PlaceMarkResponse> marksOn(UUID userId, List<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return repository.findByUserIdAndPlaceIdIn(userId, placeIds).stream()
                .map(PlaceMarkService::toResponse).toList();
    }

    /**
     * Remove every mark of a deleted user.
     *
     * @param userId the ID of the user
     * @return how many were removed
     */
    public int deleteForUser(UUID userId) {
        return repository.deleteByUserId(userId);
    }

    private static PlaceMarkResponse toResponse(PlaceMark mark) {
        return new PlaceMarkResponse(mark.getPlaceId(), mark.getKind(), mark.getVisitedOn(), mark.getCreatedAt());
    }
}
