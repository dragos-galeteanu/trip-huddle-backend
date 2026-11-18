package com.triphuddle.api.platform.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.platform.CountryCreatedContract;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.amqp.platform.CountryDeletedContract;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import com.triphuddle.amqp.platform.CountryDisabledContract;
import com.triphuddle.amqp.platform.CountryDisabledEvent;
import com.triphuddle.api.platform.dto.CountryResponse;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.api.platform.entity.Language;
import com.triphuddle.api.platform.fixtures.CountryFixtures;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import com.triphuddle.api.platform.mapper.CountryMapper;
import com.triphuddle.api.platform.repository.CountryRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock
    CountryRepository countryRepository;

    @Mock
    CurrencyService currencyService;

    @Mock
    LanguageService languageService;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    CountryMapper countryMapper = Mappers.getMapper(CountryMapper.class);

    @InjectMocks
    CountryService countryService;

    /**
     * The three codes a country must not share with another, with how a request carries each.
     */
    enum Code {
        ALPHA2(CreateCountryRequest::getAlpha2, UpdateCountryRequest::getAlpha2),
        ALPHA3(CreateCountryRequest::getAlpha3, UpdateCountryRequest::getAlpha3),
        NUMERIC(CreateCountryRequest::getNumericCode, UpdateCountryRequest::getNumericCode);

        final Function<CreateCountryRequest, String> ofCreate;
        final Function<UpdateCountryRequest, String> ofUpdate;

        Code(Function<CreateCountryRequest, String> ofCreate, Function<UpdateCountryRequest, String> ofUpdate) {
            this.ofCreate = ofCreate;
            this.ofUpdate = ofUpdate;
        }

        void givenTaken(CountryRepository repository, String value) {
            switch (this) {
                case ALPHA2 -> given(repository.existsByAlpha2(value)).willReturn(true);
                case ALPHA3 -> given(repository.existsByAlpha3(value)).willReturn(true);
                case NUMERIC -> given(repository.existsByNumericCode(value)).willReturn(true);
            }
        }

        void givenTakenByAnother(CountryRepository repository, String value, UUID id) {
            switch (this) {
                case ALPHA2 -> given(repository.existsByAlpha2AndIdNot(value, id)).willReturn(true);
                case ALPHA3 -> given(repository.existsByAlpha3AndIdNot(value, id)).willReturn(true);
                case NUMERIC -> given(repository.existsByNumericCodeAndIdNot(value, id)).willReturn(true);
            }
        }
    }

    @Nested
    class Create {

        @Test
        void savesTheCountryWithItsDefaultsAndPublishesCreated() {
            Currency eur = CurrencyFixtures.entity("EUR");
            Language fr = LanguageFixtures.entity("fr");
            CreateCountryRequest request = CountryFixtures.createRequest()
                    .defaultCurrencyId(eur.getId()).defaultLanguageId(fr.getId()).isEuMember(true).build();
            given(currencyService.getEntityById(eur.getId())).willReturn(eur);
            given(languageService.getEntityById(fr.getId())).willReturn(fr);
            given(countryRepository.saveAndFlush(any())).willAnswer(savedWithId());

            CountryResponse response = countryService.create(request);

            assertThat(response.id()).isNotNull();
            assertThat(response.alpha2()).isEqualTo(request.getAlpha2());
            assertThat(response.alpha3()).isEqualTo(request.getAlpha3());
            assertThat(response.numericCode()).isEqualTo(request.getNumericCode());
            assertThat(response.isEuMember()).isTrue();
            assertThat(response.defaultCurrencyId()).isEqualTo(eur.getId());
            assertThat(response.defaultLanguageId()).isEqualTo(fr.getId());
            then(messageProducer).should().publishAfterCommit(new CountryCreatedEvent(response.id(), request.getAlpha2()),
                    "", CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME);
        }

        @Test
        void leavesTheDefaultsEmptyWhenNoneAreGiven() {
            given(countryRepository.saveAndFlush(any())).willAnswer(savedWithId());

            CountryResponse response = countryService.create(CountryFixtures.createRequest().build());

            assertThat(response.defaultCurrencyId()).isNull();
            assertThat(response.defaultLanguageId()).isNull();
            then(currencyService).shouldHaveNoInteractions();
            then(languageService).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @EnumSource(Code.class)
        void refusesACodeAnotherCountryHas(Code code) {
            CreateCountryRequest request = CountryFixtures.createRequest().build();
            code.givenTaken(countryRepository, code.ofCreate.apply(request));

            assertThatThrownBy(() -> countryService.create(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(code.ofCreate.apply(request));
            then(countryRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void failsWithoutSavingWhenTheDefaultCurrencyDoesNotExist() {
            UUID missing = UUID.randomUUID();
            given(currencyService.getEntityById(missing)).willThrow(new ResourceNotFoundException("not found"));

            assertThatThrownBy(() -> countryService.create(CountryFixtures.createRequest()
                    .defaultCurrencyId(missing).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            then(countryRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Read {

        @Test
        void findsACountryThatIsNotDeleted() {
            Country country = CountryFixtures.entity();
            given(countryRepository.findByIdAndDeletedAtIsNull(country.getId())).willReturn(Optional.of(country));

            assertThat(countryService.getById(country.getId()).alpha2()).isEqualTo(country.getAlpha2());
        }

        @Test
        void treatsAMissingOrDeletedCountryAsNotFound() {
            UUID id = UUID.randomUUID();
            given(countryRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> countryService.getById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }

        @Test
        void listsTheRequestedPageOfCountriesNotDeleted() {
            Country country = CountryFixtures.entity();
            given(countryRepository.findAllByDeletedAtIsNull(PageRequest.of(1, 10)))
                    .willReturn(new PageImpl<>(List.of(country), PageRequest.of(1, 10), 11));

            Paginated<CountryResponse> page = countryService.getAll(new PaginationFilter(2, 10));

            assertThat(page.data()).extracting(CountryResponse::id).containsExactly(country.getId());
            assertThat(page.metaData().currentPage()).isEqualTo(2);
            assertThat(page.metaData().totalCount()).isEqualTo(11);
            assertThat(page.metaData().totalPages()).isEqualTo(2);
        }
    }

    @Nested
    class Update {

        @Test
        void appliesTheChangesAndPublishesNothing() {
            Country country = givenExisting();
            Currency usd = CurrencyFixtures.entity("USD");
            UpdateCountryRequest request = CountryFixtures.updateRequest(country.getId())
                    .defaultCurrencyId(usd.getId()).build();
            given(currencyService.getEntityById(usd.getId())).willReturn(usd);
            given(countryRepository.saveAndFlush(country)).willReturn(country);

            CountryResponse response = countryService.update(request);

            assertThat(response.name()).isEqualTo(request.getName());
            assertThat(response.alpha2()).isEqualTo(request.getAlpha2());
            assertThat(response.standardVatRate()).isEqualByComparingTo(request.getStandardVatRate());
            assertThat(response.isEuVatMember()).isTrue();
            assertThat(response.defaultCurrencyId()).isEqualTo(usd.getId());
            assertThat(response.defaultLanguageId()).isNull();
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void asksWhetherACodeIsTakenByAnotherCountryNotThisOne() {
            Country country = givenExisting();
            given(countryRepository.saveAndFlush(country)).willReturn(country);
            UpdateCountryRequest request = CountryFixtures.updateRequest(country.getId()).build();

            countryService.update(request);

            then(countryRepository).should().existsByAlpha2AndIdNot(request.getAlpha2(), country.getId());
            then(countryRepository).should().existsByAlpha3AndIdNot(request.getAlpha3(), country.getId());
            then(countryRepository).should().existsByNumericCodeAndIdNot(request.getNumericCode(), country.getId());
        }

        @ParameterizedTest
        @EnumSource(Code.class)
        void refusesACodeAnotherCountryHas(Code code) {
            Country country = givenExisting();
            UpdateCountryRequest request = CountryFixtures.updateRequest(country.getId()).build();
            code.givenTakenByAnother(countryRepository, code.ofUpdate.apply(request), country.getId());

            assertThatThrownBy(() -> countryService.update(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(code.ofUpdate.apply(request));
            then(countryRepository).should(never()).saveAndFlush(any());
        }

        @Test
        void failsForAnUnknownCountry() {
            UUID id = UUID.randomUUID();
            given(countryRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> countryService.update(CountryFixtures.updateRequest(id).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            then(countryRepository).should(never()).existsByAlpha2AndIdNot(anyString(), any());
        }
    }

    @Nested
    class DisableAndDelete {

        @Test
        void disablingStampsDisabledAtAndPublishesDisabled() {
            Country country = givenExisting();
            given(countryRepository.saveAndFlush(country)).willReturn(country);

            CountryResponse response = countryService.disable(country.getId());

            assertThat(response.disabledAt()).isNotNull();
            assertThat(response.deletedAt()).isNull();
            then(messageProducer).should().publishAfterCommit(new CountryDisabledEvent(country.getId(), country.getAlpha2()),
                    "", CountryDisabledContract.COUNTRY_DISABLED_QUEUE_NAME);
        }

        @Test
        void deletingStampsDeletedAtAndPublishesDeleted() {
            Country country = givenExisting();
            given(countryRepository.saveAndFlush(country)).willReturn(country);

            CountryResponse response = countryService.delete(country.getId());

            assertThat(response.deletedAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new CountryDeletedEvent(country.getId(), country.getAlpha2()),
                    "", CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME);
        }

        @ParameterizedTest
        @EnumSource(Change.class)
        void bothFailForAMissingOrAlreadyDeletedCountry(Change change) {
            UUID id = UUID.randomUUID();
            given(countryRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

            assertThatThrownBy(() -> change.apply.apply(countryService, id))
                    .isInstanceOf(ResourceNotFoundException.class);
            then(countryRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    enum Change {
        DISABLE(CountryService::disable),
        DELETE(CountryService::delete);

        final BiFunction<CountryService, UUID, CountryResponse> apply;

        Change(BiFunction<CountryService, UUID, CountryResponse> apply) {
            this.apply = apply;
        }
    }

    private Country givenExisting() {
        Country country = CountryFixtures.entity();
        given(countryRepository.findByIdAndDeletedAtIsNull(country.getId())).willReturn(Optional.of(country));
        return country;
    }

    private static Answer<Country> savedWithId() {
        return invocation -> {
            Country country = invocation.getArgument(0);
            country.setId(UUID.randomUUID());
            return country;
        };
    }
}
