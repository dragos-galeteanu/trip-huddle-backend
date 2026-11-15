package com.triphuddle.api.destination.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Counts what AI generation costs per UTC day, overall in tokens and per user in generations.
 */
@Repository
@RequiredArgsConstructor
public class AiUsageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Get the tokens a day spent so far.
     *
     * @param day the UTC day
     * @return input and output tokens together
     */
    public long tokensOn(LocalDate day) {
        Long tokens = jdbc.queryForObject("""
                select coalesce(sum(input_tokens + output_tokens), 0) from destination_ai_usage where day = :day
                """, new MapSqlParameterSource("day", day), Long.class);
        return tokens == null ? 0 : tokens;
    }

    /**
     * Add a finished job's tokens to its day.
     *
     * @param day the UTC day
     * @param inputTokens tokens read
     * @param outputTokens tokens written
     */
    public void addTokens(LocalDate day, long inputTokens, long outputTokens) {
        jdbc.update("""
                insert into destination_ai_usage (day, input_tokens, output_tokens, jobs)
                values (:day, :input, :output, 1)
                on conflict (day) do update set input_tokens = destination_ai_usage.input_tokens + :input,
                    output_tokens = destination_ai_usage.output_tokens + :output,
                    jobs = destination_ai_usage.jobs + 1
                """, new MapSqlParameterSource("day", day).addValue("input", inputTokens)
                .addValue("output", outputTokens));
    }

    /**
     * Count a generation a user's read started, and say how many they started that day.
     *
     * @param userId the ID of the user
     * @param day the UTC day
     * @return the day's count, this one included
     */
    public int countGeneration(UUID userId, LocalDate day) {
        Integer count = jdbc.queryForObject("""
                insert into destination_ai_user_usage (user_id, day, generations) values (:userId, :day, 1)
                on conflict (user_id, day) do update set generations = destination_ai_user_usage.generations + 1
                returning generations
                """, new MapSqlParameterSource("userId", userId).addValue("day", day), Integer.class);
        return count == null ? 0 : count;
    }
}
