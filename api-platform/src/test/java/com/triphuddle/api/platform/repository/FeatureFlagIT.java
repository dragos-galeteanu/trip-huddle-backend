package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.FeatureFlag;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PLT-4: feature flags are stored by unique key, and audited.
 */
@IntegrationTest
@Import(MongoContainerConfig.class)
class FeatureFlagIT {

    @Autowired
    MongoTemplate mongo;

    // Removes the documents but keeps the collection's indexes, which the application builds once at startup.
    @AfterEach
    void removeFlags() {
        mongo.remove(new Query(), FeatureFlag.class);
    }

    @Test
    void storesAFlagWithItsAuditTimestamps() {
        FeatureFlag saved = mongo.save(flag("semantic-search"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void refusesASecondFlagWithTheSameKey() {
        mongo.save(flag("itineraries"));

        assertThatThrownBy(() -> mongo.save(flag("itineraries"))).isInstanceOf(DuplicateKeyException.class);
    }

    private static FeatureFlag flag(String key) {
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey(key);
        flag.setName(key);
        flag.setDescription("test flag");
        return flag;
    }
}
