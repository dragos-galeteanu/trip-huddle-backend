package com.triphuddle.api.destination.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Place and query embeddings in pgvector, through SQL, as JPA has no vector type.
 */
@Repository
@RequiredArgsConstructor
public class EmbeddingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * A place waiting in an embedding job.
     *
     * @param placeId the place
     * @param position its input's index in the job
     * @param hash the hash of the text sent
     */
    public record PendingPlace(UUID placeId, int position, String hash) {
    }

    /**
     * A query's cached embedding, or the job writing it.
     *
     * @param hasEmbedding whether it has one
     * @param pendingJobId the job waiting, or null
     * @param requestedAt when it was last sent
     * @param failedAt when it last failed
     */
    public record QueryState(boolean hasEmbedding, UUID pendingJobId, Instant requestedAt, Instant failedAt) {
    }

    /**
     * A search result.
     *
     * @param placeId the place
     * @param similarity cosine similarity, 1 being the same meaning
     */
    public record Hit(UUID placeId, double similarity) {
    }

    /**
     * Find listed places whose embedding is missing or may be stale: never checked (a job that failed or gave a
     * bad vector), changed, or with newer English overview content since last checked; none already waiting in
     * a job.
     *
     * @param limit the most to find
     * @return the IDs, the longest waiting first
     */
    public List<UUID> findDue(int limit) {
        return jdbc.queryForList("""
                select p.id from destination_places p
                left join destination_place_embeddings e on e.place_id = p.id
                where p.deleted_at is null and p.status = 'ACTIVE' and e.pending_job_id is null
                  and (e.place_id is null or e.checked_at is null or e.checked_at < p.updated_at or exists (
                      select 1 from destination_place_contents c where c.place_id = p.id
                        and c.section = 'OVERVIEW' and c.language_code = 'en' and c.generated_at > e.checked_at))
                order by e.checked_at nulls first, p.updated_at
                limit :limit
                """, new MapSqlParameterSource("limit", limit), UUID.class);
    }

    /**
     * Get the hash of the text each place's embedding was made from.
     *
     * @param placeIds the places
     * @return place ID and hash, for those that have one
     */
    public Map<UUID, String> hashesOf(List<UUID> placeIds) {
        Map<UUID, String> hashes = new HashMap<>();
        if (placeIds.isEmpty()) {
            return hashes;
        }
        jdbc.query("""
                select place_id, source_hash from destination_place_embeddings
                where place_id in (:ids) and source_hash is not null
                """, new MapSqlParameterSource("ids", placeIds),
                rs -> {
                    hashes.put(rs.getObject("place_id", UUID.class), rs.getString("source_hash"));
                });
        return hashes;
    }

    /**
     * Mark places as checked without embedding them again, as their text is unchanged.
     *
     * @param placeIds the places
     * @param now when
     */
    public void markChecked(List<UUID> placeIds, Instant now) {
        if (placeIds.isEmpty()) {
            return;
        }
        jdbc.update("update destination_place_embeddings set checked_at = :now where place_id in (:ids)",
                new MapSqlParameterSource("ids", placeIds).addValue("now", Timestamp.from(now)));
    }

    /**
     * Put places in an embedding job.
     *
     * @param jobId the job
     * @param pending each place with its position and text hash
     * @param now when
     */
    public void markPending(UUID jobId, List<PendingPlace> pending, Instant now) {
        for (PendingPlace place : pending) {
            jdbc.update("""
                    insert into destination_place_embeddings (place_id, pending_job_id, pending_position,
                        pending_hash, requested_at)
                    values (:placeId, :jobId, :position, :hash, :now)
                    on conflict (place_id) do update set pending_job_id = excluded.pending_job_id,
                        pending_position = excluded.pending_position, pending_hash = excluded.pending_hash,
                        requested_at = excluded.requested_at
                    """, new MapSqlParameterSource("placeId", place.placeId()).addValue("jobId", jobId)
                    .addValue("position", place.position()).addValue("hash", place.hash())
                    .addValue("now", Timestamp.from(now)));
        }
    }

    /**
     * Find the places waiting in a job, in their inputs' order.
     *
     * @param jobId the job
     * @return the places
     */
    public List<PendingPlace> findPending(UUID jobId) {
        return jdbc.query("""
                select place_id, pending_position, pending_hash from destination_place_embeddings
                where pending_job_id = :jobId order by pending_position for update
                """, new MapSqlParameterSource("jobId", jobId), (rs, row) -> new PendingPlace(
                rs.getObject("place_id", UUID.class), rs.getInt("pending_position"), rs.getString("pending_hash")));
    }

    /**
     * Store a place's new embedding.
     *
     * @param placeId the place
     * @param vector the embedding, as pgvector text
     * @param hash the hash of the text it was made from
     * @param model the model that made it
     * @param now when
     */
    public void storePlace(UUID placeId, String vector, String hash, String model, Instant now) {
        jdbc.update("""
                update destination_place_embeddings set embedding = cast(:vector as vector), source_hash = :hash,
                    model = :model, embedded_at = :now, checked_at = :now, pending_job_id = null,
                    pending_position = null, pending_hash = null
                where place_id = :placeId
                """, new MapSqlParameterSource("placeId", placeId).addValue("vector", vector)
                .addValue("hash", hash).addValue("model", model).addValue("now", Timestamp.from(now)));
    }

    /**
     * Take places out of a job without a new embedding, so a later run tries again.
     *
     * @param jobId the job
     * @return how many were released
     */
    public int releasePending(UUID jobId) {
        return jdbc.update("""
                update destination_place_embeddings set pending_job_id = null, pending_position = null,
                    pending_hash = null where pending_job_id = :jobId
                """, new MapSqlParameterSource("jobId", jobId));
    }

    /**
     * Take places out of jobs whose answer is overdue.
     *
     * @param requestedBefore jobs sent before this are overdue
     * @return how many were released
     */
    public int releaseOverdue(Instant requestedBefore) {
        return jdbc.update("""
                update destination_place_embeddings set pending_job_id = null, pending_position = null,
                    pending_hash = null where pending_job_id is not null and requested_at < :before
                """, new MapSqlParameterSource("before", Timestamp.from(requestedBefore)));
    }

    /**
     * Get a query's state, adding it if new, and lock it.
     *
     * @param hash the hash of the normalised query
     * @param text the normalised query
     * @param now when
     * @return the state
     */
    public QueryState lockQuery(String hash, String text, Instant now) {
        jdbc.update("""
                insert into destination_query_embeddings (query_hash, query_text, last_used_at)
                values (:hash, :text, :now)
                on conflict (query_hash) do update set last_used_at = excluded.last_used_at
                """, new MapSqlParameterSource("hash", hash).addValue("text", text)
                .addValue("now", Timestamp.from(now)));
        return jdbc.queryForObject("""
                select embedding is not null as has_embedding, pending_job_id, requested_at, failed_at
                from destination_query_embeddings where query_hash = :hash for update
                """, new MapSqlParameterSource("hash", hash), (rs, row) -> new QueryState(
                rs.getBoolean("has_embedding"), rs.getObject("pending_job_id", UUID.class),
                instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("failed_at"))));
    }

    /**
     * Mark a query as sent for embedding.
     *
     * @param hash the hash of the query
     * @param jobId the job
     * @param now when
     */
    public void markQueryPending(String hash, UUID jobId, Instant now) {
        jdbc.update("""
                update destination_query_embeddings set pending_job_id = :jobId, requested_at = :now
                where query_hash = :hash
                """, new MapSqlParameterSource("hash", hash).addValue("jobId", jobId)
                .addValue("now", Timestamp.from(now)));
    }

    /**
     * Store a query's embedding, or its failure, if the job is still waited for.
     *
     * @param jobId the job
     * @param vector the embedding as pgvector text, or null if it failed
     * @param model the model
     * @param now when
     * @return true if a query was waiting for it
     */
    public boolean storeQuery(UUID jobId, String vector, String model, Instant now) {
        String sql = vector == null
                ? "update destination_query_embeddings set pending_job_id = null, failed_at = :now"
                + " where pending_job_id = :jobId"
                : "update destination_query_embeddings set embedding = cast(:vector as vector), model = :model,"
                + " embedded_at = :now, pending_job_id = null, failed_at = null where pending_job_id = :jobId";
        return jdbc.update(sql, new MapSqlParameterSource("jobId", jobId).addValue("vector", vector)
                .addValue("model", model).addValue("now", Timestamp.from(now))) > 0;
    }

    /**
     * Find the listed places nearest in meaning to a query.
     *
     * @param queryHash the query's hash; it must have an embedding
     * @param countryCode only in this country, or null
     * @param kind only of this kind, or null
     * @param withinPath only within this subtree, or null
     * @param limit the most to find
     * @return the hits, the nearest first
     */
    public List<Hit> searchByQuery(String queryHash, String countryCode, String kind, String withinPath,
                                   int limit) {
        return search("(select q.embedding from destination_query_embeddings q where q.query_hash = :key)",
                new MapSqlParameterSource("key", queryHash), null, countryCode, kind, withinPath, limit);
    }

    /**
     * Find the listed places nearest in meaning to a place.
     *
     * @param placeId the place; it must have an embedding
     * @param countryCode only in this country, or null
     * @param limit the most to find
     * @return the hits, the nearest first, the place itself left out
     */
    public List<Hit> searchByPlace(UUID placeId, String countryCode, int limit) {
        return search("(select s.embedding from destination_place_embeddings s where s.place_id = :key)",
                new MapSqlParameterSource("key", placeId), placeId, countryCode, null, null, limit);
    }

    /**
     * Check if a place has an embedding.
     *
     * @param placeId the place
     * @return true if it has one
     */
    public boolean hasEmbedding(UUID placeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from destination_place_embeddings where place_id = :id
                    and embedding is not null)
                """, new MapSqlParameterSource("id", placeId), Boolean.class));
    }

    private List<Hit> search(String target, MapSqlParameterSource params, UUID excludeId, String countryCode,
                             String kind, String withinPath, int limit) {
        List<String> where = new ArrayList<>(List.of("e.embedding is not null", "p.deleted_at is null",
                "p.status = 'ACTIVE'"));
        if (excludeId != null) {
            where.add("p.id <> :excludeId");
            params.addValue("excludeId", excludeId);
        }
        if (countryCode != null) {
            where.add("p.country_code = :countryCode");
            params.addValue("countryCode", countryCode);
        }
        if (kind != null) {
            where.add("p.kind = :kind");
            params.addValue("kind", kind);
        }
        if (withinPath != null) {
            where.add("p.path like :withinPath");
            params.addValue("withinPath", withinPath + "%");
        }
        params.addValue("limit", limit);
        // The distance is to the target's own vector, read once by the subquery.
        return jdbc.query("""
                select p.id, 1 - (e.embedding <=> %s) as similarity
                from destination_place_embeddings e join destination_places p on p.id = e.place_id
                where %s
                order by e.embedding <=> %s
                limit :limit
                """.formatted(target, String.join(" and ", where), target), params,
                (rs, row) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("similarity")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
