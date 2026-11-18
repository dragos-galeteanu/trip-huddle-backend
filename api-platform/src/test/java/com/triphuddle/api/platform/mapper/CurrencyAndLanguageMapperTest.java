package com.triphuddle.api.platform.mapper;

import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.CurrencyResponse;
import com.triphuddle.api.platform.dto.LanguageResponse;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.api.platform.entity.Language;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyAndLanguageMapperTest {

    private final CurrencyMapper currencyMapper = Mappers.getMapper(CurrencyMapper.class);
    private final LanguageMapper languageMapper = Mappers.getMapper(LanguageMapper.class);

    @Test
    void carriesEveryCurrencyField() {
        CreateCurrencyRequest create = CurrencyFixtures.createRequest().minorUnits(3).build();
        Currency currency = new Currency();

        currencyMapper.createEntityFromRequest(currency, create);
        CurrencyResponse created = currencyMapper.toResponse(currency);

        assertThat(created.code()).isEqualTo(create.getCode());
        assertThat(created.name()).isEqualTo(create.getName());
        assertThat(created.symbol()).isEqualTo(create.getSymbol());
        assertThat(created.symbolPosition()).isEqualTo(create.getSymbolPosition());
        assertThat(created.minorUnits()).isEqualTo(3);

        UUID id = UUID.randomUUID();
        currency.setId(id);
        UpdateCurrencyRequest update = CurrencyFixtures.updateRequest(UUID.randomUUID()).build();
        currencyMapper.updateEntityFromRequest(currency, update);
        assertThat(currency.getId()).as("an update never re-keys the row").isEqualTo(id);
        assertThat(currencyMapper.toResponse(currency).symbolPosition()).isEqualTo(update.getSymbolPosition());
        assertThat(currencyMapper.toResponse(null)).isNull();
    }

    @Test
    void carriesEveryLanguageFieldIncludingTheWritingDirection() {
        CreateLanguageRequest create = LanguageFixtures.createRequest().isRightToLeft(true).build();
        Language language = new Language();

        languageMapper.createEntityFromRequest(language, create);
        LanguageResponse created = languageMapper.toResponse(language);

        assertThat(created.code()).isEqualTo(create.getCode());
        assertThat(created.nativeName()).isEqualTo(create.getNativeName());
        assertThat(created.isRightToLeft()).isTrue();

        UUID id = UUID.randomUUID();
        language.setId(id);
        UpdateLanguageRequest update = LanguageFixtures.updateRequest(UUID.randomUUID()).isRightToLeft(false).build();
        languageMapper.updateEntityFromRequest(language, update);
        assertThat(language.getId()).as("an update never re-keys the row").isEqualTo(id);
        assertThat(languageMapper.toResponse(language).isRightToLeft()).isFalse();
        assertThat(languageMapper.toResponse(null)).isNull();
    }
}
