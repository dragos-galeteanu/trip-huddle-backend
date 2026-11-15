package com.triphuddle.api.destination.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Searches places in SQL: names by trigram similarity and full text, both forgiving accents through
 * {@code destination_norm}; points with PostGIS; subtrees by path prefix.
 */
@Repository
@RequiredArgsConstructor
public class PlaceSearchRepository {

    // The point a radius search is around, as geography so distances are in metres.
    private static final String POINT = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Find a page of places: by match when there are words, else by distance when there is a point, else featured
     * and most populous first.
     *
     * @param query what to search for
     * @param pageable the page
     * @return a page of hits
     */
    public Page<PlaceSearchHit> search(PlaceSearchQuery query, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String from = fromAndWhere(query, params);

        String select = "select p.id, %s as score, %s as distance ".formatted(
                query.text() != null ? "m.score" : "null::float8",
                query.hasPoint() ? "ST_Distance(p.geo, " + POINT + ")" : "null::float8");
        String order;
        if (query.text() != null) {
            order = " order by m.score desc, p.population desc nulls last, p.name, p.id";
        } else if (query.hasPoint()) {
            order = " order by distance asc nulls last, p.name, p.id";
        } else {
            order = " order by p.is_featured desc, p.population desc nulls last, p.name, p.id";
        }
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PlaceSearchHit> hits = jdbc.query(select + from + order + " limit :limit offset :offset", params,
                (row, i) -> new PlaceSearchHit(row.getObject("id", UUID.class),
                        row.getObject("score", Double.class), row.getObject("distance", Double.class)));
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        return new PageImpl<>(hits, pageable, total == null ? 0 : total);
    }

    /**
     * Find the nearest listed city to a point in a country, within a distance.
     *
     * @param countryCode the country, ISO 3166 alpha-2
     * @param latitude the point's latitude
     * @param longitude the point's longitude
     * @param maxMeters the furthest the city may be
     * @return the city's ID, or null if none is that close
     */
    public UUID nearestCity(String countryCode, double latitude, double longitude, double maxMeters) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("countryCode", countryCode)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("maxMeters", maxMeters);
        List<UUID> ids = jdbc.query("""
                select p.id from destination_places p
                where p.country_code = :countryCode and p.kind = 'CITY' and p.status = 'ACTIVE'
                  and p.deleted_at is null and ST_DWithin(p.geo, %s, :maxMeters)
                order by p.geo <-> %s
                limit 1
                """.formatted(POINT, POINT), params, (row, i) -> row.getObject("id", UUID.class));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String fromAndWhere(PlaceSearchQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("from destination_places p ");
        List<String> where = new ArrayList<>();
        if (query.text() != null) {
            // Every name a place is known by counts, and the best one ranks it.
            sql.append("""
                    join (select n.place_id,
                                 max(similarity(destination_norm(n.name), destination_norm(:text)))::float8 as score
                          from destination_place_names n
                          where destination_norm(n.name) % destination_norm(:text)
                             or to_tsvector('simple', destination_norm(n.name))
                                @@ plainto_tsquery('simple', destination_norm(:text))
                          group by n.place_id) m on m.place_id = p.id
                    """);
            params.addValue("text", query.text());
        }
        where.add("p.deleted_at is null");
        where.add(query.isIncludingHidden() ? "p.status <> 'MERGED'" : "p.status = 'ACTIVE'");
        if (query.countryCode() != null) {
            where.add("p.country_code = :countryCode");
            params.addValue("countryCode", query.countryCode());
        }
        if (query.kind() != null) {
            where.add("p.kind = :kind");
            params.addValue("kind", query.kind().name());
        }
        if (query.parentId() != null) {
            where.add("p.parent_id = :parentId");
            params.addValue("parentId", query.parentId());
        }
        if (query.withinPath() != null) {
            where.add("p.path like :withinPath and p.id <> :withinId");
            params.addValue("withinPath", query.withinPath() + "%");
            params.addValue("withinId", query.withinId());
        }
        if (query.categoryPath() != null) {
            where.add("""
                    exists (select 1 from destination_place_categories pc
                            join destination_categories c on c.id = pc.category_id
                            where pc.place_id = p.id and c.deleted_at is null and c.path like :categoryPath)""");
            params.addValue("categoryPath", query.categoryPath() + "%");
        }
        if (query.tag() != null) {
            where.add("exists (select 1 from destination_place_tags t where t.place_id = p.id and t.tag = :tag)");
            params.addValue("tag", query.tag());
        }
        if (query.isFeatured() != null) {
            where.add("p.is_featured = :isFeatured");
            params.addValue("isFeatured", query.isFeatured());
        }
        if (query.hasPoint()) {
            params.addValue("latitude", query.latitude());
            params.addValue("longitude", query.longitude());
            if (query.radiusMeters() != null) {
                where.add("ST_DWithin(p.geo, " + POINT + ", :radiusMeters)");
                params.addValue("radiusMeters", query.radiusMeters());
            }
        }
        if (query.hasArea()) {
            where.add("p.geo && ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography");
            params.addValue("west", query.west());
            params.addValue("south", query.south());
            params.addValue("east", query.east());
            params.addValue("north", query.north());
        }
        return sql + "where " + String.join(" and ", where);
    }
}
