package com.triphuddle.api.user.entity;

import com.triphuddle.common.entity.BaseMongoDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "user_preferences")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserPreference extends BaseMongoDocument {

    @Indexed(unique = true)
    private UUID userId;

    private String languageCode;

    private String countryCode;

    private boolean isMarketingOptIn = true;
}
