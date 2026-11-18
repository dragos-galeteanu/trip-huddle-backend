package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.VisaRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The visa rules table, read and loaded in bulk with SQL; tens of thousands of rows are too many for entities.
 */
@Repository
@RequiredArgsConstructor
public class VisaRuleRepository {

    private static final int BATCH_SIZE = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Insert or update rules, marking them as loaded by an import.
     *
     * @param rules the rules
     * @param importId the import loading them
     * @param now when
     */
    public void upsertAll(List<VisaRule> rules, UUID importId, Instant now) {
        for (int from = 0; from < rules.size(); from += BATCH_SIZE) {
            SqlParameterSource[] batch = rules.subList(from, Math.min(from + BATCH_SIZE, rules.size())).stream()
                    .map(rule -> new MapSqlParameterSource("passport", rule.passportCode())
                            .addValue("destination", rule.destinationCode())
                            .addValue("requirement", rule.requirement().name())
                            .addValue("days", rule.allowedDays())
                            .addValue("importId", importId)
                            .addValue("now", Timestamp.from(now)))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    insert into destination_visa_rules
                        (passport_code, destination_code, requirement, allowed_days, import_id, updated_at)
                    values (:passport, :destination, :requirement, :days, :importId, :now)
                    on conflict (passport_code, destination_code) do update
                        set requirement = excluded.requirement, allowed_days = excluded.allowed_days,
                            import_id = excluded.import_id, updated_at = excluded.updated_at
                    """, batch);
        }
    }

    /**
     * Remove the rules an import didn't load, which the dataset no longer has.
     *
     * @param importId the import
     * @return how many were removed
     */
    public int deleteNotLoadedBy(UUID importId) {
        return jdbc.update("delete from destination_visa_rules where import_id <> :importId",
                new MapSqlParameterSource("importId", importId));
    }

    /**
     * Find what each of several passports needs for a destination.
     *
     * @param passportCodes the passports' countries
     * @param destinationCode the destination
     * @return the rules found
     */
    public List<VisaRule> find(Collection<String> passportCodes, String destinationCode) {
        if (passportCodes.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select passport_code, destination_code, requirement, allowed_days from destination_visa_rules
                where passport_code in (:passports) and destination_code = :destination
                """, new MapSqlParameterSource("passports", passportCodes).addValue("destination", destinationCode),
                VisaRuleRepository::map);
    }

    private static VisaRule map(ResultSet rs, int row) throws SQLException {
        return new VisaRule(rs.getString("passport_code"), rs.getString("destination_code"),
                VisaRequirement.valueOf(rs.getString("requirement")), rs.getObject("allowed_days", Integer.class));
    }
}
