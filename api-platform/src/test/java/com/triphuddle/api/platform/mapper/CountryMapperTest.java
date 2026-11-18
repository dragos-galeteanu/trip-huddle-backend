package com.triphuddle.api.platform.mapper;

import com.triphuddle.api.platform.dto.CountryResponse;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.api.platform.fixtures.CountryFixtures;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CountryMapperTest {

    private final CountryMapper mapper = Mappers.getMapper(CountryMapper.class);

    @Test
    void carriesEveryMembershipFlagFromRequestToResponse() {
        CreateCountryRequest request = CountryFixtures.createRequest()
                .isEuVatMember(true).isEuMember(false).isEeaMember(true).isSchengenMember(false).build();
        Country country = new Country();

        mapper.createEntityFromRequest(country, request);
        CountryResponse response = mapper.toResponse(country);

        assertThat(response.isEuVatMember()).isTrue();
        assertThat(response.isEuMember()).isFalse();
        assertThat(response.isEeaMember()).isTrue();
        assertThat(response.isSchengenMember()).isFalse();
        assertThat(response.alpha2()).isEqualTo(request.getAlpha2());
        assertThat(response.standardVatRate()).isEqualByComparingTo(request.getStandardVatRate());
    }

    @Test
    void exposesTheDefaultsByIdButNeverTakesThemFromARequest() {
        Country country = CountryFixtures.entity();
        country.setDefaultCurrency(CurrencyFixtures.entity("EUR"));
        country.setDefaultLanguage(LanguageFixtures.entity("fr"));

        mapper.updateEntityFromRequest(country, CountryFixtures.updateRequest(country.getId())
                .defaultCurrencyId(UUID.randomUUID()).defaultLanguageId(UUID.randomUUID()).build());
        CountryResponse response = mapper.toResponse(country);

        assertThat(response.defaultCurrencyId()).isEqualTo(country.getDefaultCurrency().getId());
        assertThat(response.defaultLanguageId()).isEqualTo(country.getDefaultLanguage().getId());
    }

    @Test
    void anUpdateLeavesTheIdAndAuditFieldsAlone() {
        Country country = CountryFixtures.entity();
        UUID id = country.getId();
        Instant createdAt = country.getCreatedAt();
        UpdateCountryRequest request = CountryFixtures.updateRequest(UUID.randomUUID()).build();

        mapper.updateEntityFromRequest(country, request);

        assertThat(country.getId()).isEqualTo(id);
        assertThat(country.getCreatedAt()).isEqualTo(createdAt);
        assertThat(country.getName()).isEqualTo(request.getName());
    }

    @Test
    void mapsNothingToNothingAndListsInOrder() {
        Country first = CountryFixtures.entity();
        Country second = CountryFixtures.entity();

        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toResponseList(List.of(first, second))).extracting(CountryResponse::id)
                .containsExactly(first.getId(), second.getId());
    }
}
