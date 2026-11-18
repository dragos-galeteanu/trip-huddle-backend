package com.triphuddle.api.destination.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Counts a subscriber's use of a quota entitlement per month, taking a unit only while some are left.
 */
@Repository
@RequiredArgsConstructor
public class QuotaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Take one unit of a quota, if fewer than the limit were taken this month.
     *
     * @param ownerType USER or GROUP
     * @param ownerId the ID of the user or group
     * @param key the entitlement key
     * @param period the month, as yyyy-MM
     * @param limit the most units a month
     * @return true if one was taken
     */
    public boolean tryTake(LicenseTarget ownerType, UUID ownerId, String key, String period, int limit) {
        if (limit <= 0) {
            return false;
        }
        // One statement, so two requests at once can't both take the last unit.
        List<Integer> used = jdbc.queryForList("""
                insert into destination_ai_quota_usage (owner_type, owner_id, entitlement_key, period, used)
                values (:ownerType, :ownerId, :key, :period, 1)
                on conflict (owner_type, owner_id, entitlement_key, period)
                do update set used = destination_ai_quota_usage.used + 1
                where destination_ai_quota_usage.used < :limit
                returning used
                """, params(ownerType, ownerId, key, period).addValue("limit", limit), Integer.class);
        return !used.isEmpty();
    }

    /**
     * Give a unit back, e.g. when what it paid for failed.
     *
     * @param ownerType USER or GROUP
     * @param ownerId the ID of the user or group
     * @param key the entitlement key
     * @param period the month it was taken in, as yyyy-MM
     */
    public void giveBack(LicenseTarget ownerType, UUID ownerId, String key, String period) {
        jdbc.update("""
                update destination_ai_quota_usage set used = greatest(used - 1, 0)
                where owner_type = :ownerType and owner_id = :ownerId and entitlement_key = :key and period = :period
                """, params(ownerType, ownerId, key, period));
    }

    private static MapSqlParameterSource params(LicenseTarget ownerType, UUID ownerId, String key, String period) {
        return new MapSqlParameterSource("ownerType", ownerType.name()).addValue("ownerId", ownerId)
                .addValue("key", key).addValue("period", period);
    }
}
