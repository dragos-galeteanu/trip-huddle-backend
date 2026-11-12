package com.triphuddle.api.platform.entity;

import com.triphuddle.common.entity.BaseMongoDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A feature flag. Managed by admins directly on the database, hence no service or controller.
 */
@Document(collection = "feature_flags")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FeatureFlag extends BaseMongoDocument {

    @Indexed(unique = true)
    private String flagKey;

    private String name;

    private String description;
}
